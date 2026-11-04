// Polls the Lead object as the signed-in user and redraws the board. Only first names, company
// names, segments, scores and the agents' own words reach the screen: never an email address and
// never the visitor's free-text notes.

const LEADS_URL =
	'/o/c/devconleads?pageSize=500&sort=dateCreated:desc&fields=' +
	'classificationRationale,companyName,contactName,dateCreated,fitScore,' +
	'id,industry,outreachSubject,reviewNotes,segment,triageStatus';

const HIDDEN_STORAGE_KEY = 'devcon-board-hidden';

const SEGMENTS = [
	{key: 'enterprise', label: 'Enterprise'},
	{key: 'midMarket', label: 'Mid-Market'},
	{key: 'smb', label: 'SMB'},
	{key: 'disqualified', label: 'Disqualified'},
];

const STATUS_LABELS = {
	channelOpened: 'Channel opened',
	failed: 'Stopped',
	new: 'Received',
	qualified: 'Qualified',
	rejected: 'Declined',
	triaging: 'AI Hub thinking',
};

const maxCards = Math.max(1, parseInt(configuration.maxCards, 10) || 9);
const refreshMilliseconds =
	Math.max(1, parseInt(configuration.refreshSeconds, 10) || 3) * 1000;

const averageElement = fragmentElement.querySelector('[data-board-average]');
const barsElement = fragmentElement.querySelector('[data-board-bars]');
const cardsElement = fragmentElement.querySelector('[data-board-cards]');
const liveElement = fragmentElement.querySelector('[data-board-live]');
const messageElement = fragmentElement.querySelector('[data-board-message]');
const totalElement = fragmentElement.querySelector('[data-board-total]');
const updatedElement = fragmentElement.querySelector('[data-board-updated]');

// The QR code to the lead form lets the room keep sending leads while the board is on screen
if (configuration.showQrCode === false || configuration.showQrCode === 'false') {
	fragmentElement.querySelector('[data-board-qr]').hidden = true;
}

const knownStatuses = new Map();

let firstDraw = true;

function getKey(value) {
	return value && typeof value === 'object' ? value.key : value;
}

function getName(value) {
	return value && typeof value === 'object' ? value.name : value || '';
}

function getFirstName(contactName) {
	return (contactName || '').trim().split(/\s+/)[0];
}

function getHiddenIds() {
	try {
		return new Set(
			JSON.parse(sessionStorage.getItem(HIDDEN_STORAGE_KEY) || '[]')
		);
	}
	catch (error) {
		return new Set();
	}
}

function hideLead(id) {
	const hiddenIds = getHiddenIds();

	hiddenIds.add(id);

	try {
		sessionStorage.setItem(
			HIDDEN_STORAGE_KEY,
			JSON.stringify(Array.from(hiddenIds))
		);
	}
	catch (error) {
		// The card comes back on the next refresh; nothing else to do
	}

	const cardElement = cardsElement.querySelector(`[data-lead-id="${id}"]`);

	if (cardElement) {
		cardElement.remove();
	}
}

function element(tagName, className, text) {
	const newElement = document.createElement(tagName);

	if (className) {
		newElement.className = className;
	}

	if (text !== undefined && text !== null) {
		newElement.textContent = text;
	}

	return newElement;
}

function getDetail(lead, status) {
	if (status === 'channelOpened' || status === 'qualified') {
		return lead.outreachSubject ?
			'✉ ' + lead.outreachSubject :
			lead.classificationRationale;
	}

	// Declined by the business rule, not by the classifier: show the rule

	if (status === 'rejected') {
		return getKey(lead.segment) !== 'disqualified' && lead.reviewNotes ?
			lead.reviewNotes :
			lead.classificationRationale;
	}

	if (status === 'failed') {
		return 'Stopped before any message went out.';
	}

	return 'Classifier and outreach agents at work…';
}

function renderCard(lead) {
	const status = getKey(lead.triageStatus) || 'new';
	const segment = getKey(lead.segment);

	const cardElement = element(
		'li',
		'devcon-board__card devcon-board__card--' + status
	);

	cardElement.dataset.leadId = lead.id;

	const previousStatus = knownStatuses.get(lead.id);

	if (!firstDraw && previousStatus === undefined) {
		cardElement.classList.add('is-new');
	}
	else if (previousStatus && previousStatus !== status) {
		cardElement.classList.add('is-changed');
	}

	knownStatuses.set(lead.id, status);

	const headerElement = element('div', 'devcon-board__card-header');

	const nameElement = element('div', 'devcon-board__card-names');

	nameElement.appendChild(
		element('strong', 'devcon-board__card-company', lead.companyName)
	);

	const firstName = getFirstName(lead.contactName);
	const industry = getName(lead.industry);

	nameElement.appendChild(
		element(
			'span',
			'devcon-board__card-person',
			[firstName, industry].filter(Boolean).join(' · ')
		)
	);

	headerElement.appendChild(nameElement);

	if (lead.fitScore !== undefined && lead.fitScore !== null) {
		headerElement.appendChild(
			element('span', 'devcon-board__card-score', String(lead.fitScore))
		);
	}

	cardElement.appendChild(headerElement);

	const tagsElement = element('div', 'devcon-board__card-tags');

	tagsElement.appendChild(
		element(
			'span',
			'devcon-board__pill devcon-board__pill--' + status,
			STATUS_LABELS[status] || status
		)
	);

	if (segment) {
		tagsElement.appendChild(
			element('span', 'devcon-board__segment-tag', getName(lead.segment))
		);
	}

	cardElement.appendChild(tagsElement);

	const detail = getDetail(lead, status);

	if (detail) {
		cardElement.appendChild(
			element('p', 'devcon-board__card-detail', detail)
		);
	}

	const hideButton = element('button', 'devcon-board__card-hide', '×');

	hideButton.setAttribute('aria-label', 'Hide this lead from the board');
	hideButton.setAttribute('title', 'Hide from the board');
	hideButton.type = 'button';

	hideButton.addEventListener('click', () => hideLead(lead.id));

	cardElement.appendChild(hideButton);

	return cardElement;
}

function renderBars(leads) {
	const counts = {};

	// A lead rejected for a low fit score keeps the classifier's segment on
	// the record, but on the chart every lead the funnel declined counts as
	// disqualified

	leads.forEach((lead) => {
		const segment =
			getKey(lead.triageStatus) === 'rejected' ?
				'disqualified' :
				getKey(lead.segment);

		if (segment) {
			counts[segment] = (counts[segment] || 0) + 1;
		}
	});

	const classified = SEGMENTS.reduce(
		(total, segment) => total + (counts[segment.key] || 0),
		0
	);

	SEGMENTS.forEach((segment) => {
		let barElement = barsElement.querySelector(
			`[data-segment="${segment.key}"]`
		);

		if (!barElement) {
			barElement = element(
				'li',
				'devcon-board__bar devcon-board__bar--' + segment.key
			);

			barElement.dataset.segment = segment.key;

			const labelElement = element('div', 'devcon-board__bar-label');

			labelElement.appendChild(element('span', null, segment.label));
			labelElement.appendChild(
				element('strong', 'devcon-board__bar-count', '0')
			);

			barElement.appendChild(labelElement);

			const trackElement = element('div', 'devcon-board__bar-track');

			trackElement.appendChild(element('div', 'devcon-board__bar-fill'));

			barElement.appendChild(trackElement);

			barsElement.appendChild(barElement);
		}

		const count = counts[segment.key] || 0;

		barElement.querySelector('.devcon-board__bar-count').textContent =
			count;
		barElement.querySelector('.devcon-board__bar-fill').style.width =
			(classified ? (count * 100) / classified : 0) + '%';
	});

	const scores = leads
		.map((lead) => lead.fitScore)
		.filter((score) => typeof score === 'number');

	averageElement.textContent = scores.length ?
		Math.round(
			scores.reduce((total, score) => total + score, 0) / scores.length
		) :
		'–';
}

function renderCounts(leads) {
	const counts = {
		channelOpened: 0,
		failed: 0,
		qualified: 0,
		rejected: 0,
		thinking: 0,
	};

	leads.forEach((lead) => {
		const status = getKey(lead.triageStatus) || 'new';

		if (status === 'new' || status === 'triaging') {
			counts.thinking++;
		}
		else if (counts[status] !== undefined) {
			counts[status]++;
		}

		// qualified lasts only until the Open Channel action runs, so the tile
		// counts every lead that got through triage

		if (status === 'channelOpened') {
			counts.qualified++;
		}
	});

	Object.keys(counts).forEach((key) => {
		fragmentElement.querySelector(`[data-board-count="${key}"]`)
			.textContent = counts[key];
	});

	totalElement.textContent = leads.length;
}

function render(leads) {
	renderCounts(leads);
	renderBars(leads);

	const hiddenIds = getHiddenIds();

	cardsElement.replaceChildren(
		...leads
			.filter((lead) => !hiddenIds.has(lead.id))
			.slice(0, maxCards)
			.map(renderCard)
	);

	firstDraw = false;
}

function showMessage(text) {
	messageElement.hidden = !text;
	messageElement.textContent = text || '';
}

async function refresh() {
	if (document.hidden) {
		return;
	}

	try {
		const response = await Liferay.Util.fetch(LEADS_URL, {
			headers: {Accept: 'application/json'},
		});

		if (!response.ok) {
			throw new Error('HTTP ' + response.status);
		}

		const page = await response.json();

		render(page.items || []);
		showMessage(null);

		liveElement.classList.remove('is-stale');
		updatedElement.textContent =
			'Live · ' + new Date().toLocaleTimeString();
	}
	catch (error) {
		liveElement.classList.add('is-stale');
		updatedElement.textContent = 'Reconnecting…';

		console.error('Unable to refresh the leads board', error);
	}
}

async function poll() {
	if (!document.body.contains(fragmentElement)) {
		return;
	}

	await refresh();

	setTimeout(poll, refreshMilliseconds);
}

if (!themeDisplay.isSignedIn()) {
	showMessage('Sign in to see the leads board.');

	updatedElement.textContent = 'Signed out';
}
else if (typeof layoutMode !== 'undefined' && layoutMode === 'edit') {
	refresh();
}
else {
	poll();
}
