import http from 'k6/http';
import { check } from 'k6';
import {
  BASE_URL,
  LOADTEST_USER_COUNT,
  LOADTEST_USER_EMAIL_PREFIX,
  LOADTEST_USER_EMAIL_SUFFIX,
  LOADTEST_USER_PASSWORD,
  PATH_LOGIN,
} from './config.js';

export function login(userIndex) {
  const email = `${LOADTEST_USER_EMAIL_PREFIX}${userIndex}${LOADTEST_USER_EMAIL_SUFFIX}`;
  const payload = JSON.stringify({ email, password: LOADTEST_USER_PASSWORD });
  const res = http.post(`${BASE_URL}${PATH_LOGIN}`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
  });

  check(res, {
    'login status 200': (r) => r.status === 200,
    'login returns authorization header': (r) => !!r.headers['Authorization'],
  });

  const authHeader = res.headers['Authorization'];
  if (!authHeader) {
    throw new Error(`Login failed for ${email}: status=${res.status} body=${res.body}`);
  }
  return authHeader;
}

export function setupAllTokens() {
  const tokens = [];
  for (let i = 1; i <= LOADTEST_USER_COUNT; i++) {
    tokens.push(login(i));
  }
  return tokens;
}

export function pickToken(tokens) {
  return tokens[Math.floor(Math.random() * tokens.length)];
}
