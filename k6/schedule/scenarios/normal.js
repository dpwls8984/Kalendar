import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, PATH_BY_ARTISTS, PATH_FOLLOWING, YEAR } from '../helpers/config.js';
import { randomArtistIds, randomMonth } from '../helpers/artistIds.js';
import { setupAllTokens, pickToken } from '../helpers/auth.js';

export const options = {
  scenarios: {
    guest: {
      executor: 'ramping-arrival-rate',
      exec: 'guestFlow',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 20,
      maxVUs: 50,
      stages: [
        { target: 4, duration: '30s' },
        { target: 4, duration: '2m' },
        { target: 0, duration: '10s' },
      ],
    },
    member: {
      executor: 'ramping-arrival-rate',
      exec: 'memberFlow',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 10,
      maxVUs: 30,
      stages: [
        { target: 1, duration: '30s' },
        { target: 1, duration: '2m' },
        { target: 0, duration: '10s' },
      ],
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{endpoint:by-artists}': ['p(95)<300'],
    'http_req_duration{endpoint:following}': ['p(95)<300'],
  },
};

const byArtistsTrend = new Trend('schedule_by_artists_ms', true);
const followingTrend = new Trend('schedule_following_ms', true);

export function setup() {
  console.log('[setup] Logging in loadtest users...');
  const tokens = setupAllTokens();
  console.log(`[setup] Acquired ${tokens.length} tokens`);
  return { tokens };
}

export function guestFlow() {
  const artistIds = randomArtistIds(2, 10);
  const month = randomMonth();
  const url = `${BASE_URL}${PATH_BY_ARTISTS}?year=${YEAR}&month=${month}&artistIds=${artistIds.join(',')}`;
  const res = http.get(url, {
    tags: { endpoint: 'by-artists', scenario: 'normal' },
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
    tags: { endpoint: 'following', scenario: 'normal' },
  });
  followingTrend.add(res.timings.duration);
  check(res, {
    'following status 200': (r) => r.status === 200,
  });
}
