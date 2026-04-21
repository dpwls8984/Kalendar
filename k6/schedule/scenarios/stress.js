import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, PATH_BY_ARTISTS, PATH_FOLLOWING, YEAR } from '../helpers/config.js';
import { randomArtistIds, randomMonth } from '../helpers/artistIds.js';
import { setupAllTokens, pickToken } from '../helpers/auth.js';

const GUEST_RATIO = 0.7;
const MEMBER_RATIO = 0.3;

const STEPS = [100, 250, 500, 1000, 1500, 2000, 3000, 4000, 5000];
const STEP_HOLD = '45s';
const STEP_RAMP = '15s';

function buildStages(totalSteps, ratio) {
  const stages = [];
  for (const rps of totalSteps) {
    const target = Math.round(rps * ratio);
    stages.push({ target, duration: STEP_RAMP });
    stages.push({ target, duration: STEP_HOLD });
  }
  stages.push({ target: 0, duration: '10s' });
  return stages;
}

export const options = {
  scenarios: {
    guest: {
      executor: 'ramping-arrival-rate',
      exec: 'guestFlow',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 500,
      maxVUs: 4000,
      stages: buildStages(STEPS, GUEST_RATIO),
    },
    member: {
      executor: 'ramping-arrival-rate',
      exec: 'memberFlow',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 2000,
      stages: buildStages(STEPS, MEMBER_RATIO),
    },
  },
  thresholds: {
    'http_req_failed': [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{endpoint:by-artists}': [{ threshold: 'p(95)<300', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{endpoint:following}':  [{ threshold: 'p(95)<300', abortOnFail: true, delayAbortEval: '30s' }],
  },
};

const byArtistsTrend = new Trend('schedule_by_artists_ms', true);
const followingTrend = new Trend('schedule_following_ms', true);

export function setup() {
  console.log('[setup] Logging in loadtest users...');
  const tokens = setupAllTokens();
  console.log(`[setup] Acquired ${tokens.length} tokens`);
  console.log(`[setup] Steps (total RPS): ${STEPS.join(' -> ')}`);
  return { tokens };
}

export function guestFlow() {
  const artistIds = randomArtistIds(2, 10);
  const month = randomMonth();
  const url = `${BASE_URL}${PATH_BY_ARTISTS}?year=${YEAR}&month=${month}&artistIds=${artistIds.join(',')}`;
  const res = http.get(url, {
    tags: { endpoint: 'by-artists', scenario: 'stress' },
  });
  byArtistsTrend.add(res.timings.duration);
  check(res, {
    'by-artists status 200': (r) => r.status === 200,
  });
}

export function memberFlow(data) {
  const token = pickToken(data.tokens);
  const month = randomMonth();
  const url = `${BASE_URL}${PATH_FOLLOWING}?year=${YEAR}&month=${month}`;
  const res = http.get(url, {
    headers: { Authorization: token },
    tags: { endpoint: 'following', scenario: 'stress' },
  });
  followingTrend.add(res.timings.duration);
  check(res, {
    'following status 200': (r) => r.status === 200,
  });
}
