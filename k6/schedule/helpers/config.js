export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export const LOADTEST_USER_COUNT = 100;
export const LOADTEST_USER_EMAIL_PREFIX = 'loadtest';
export const LOADTEST_USER_EMAIL_SUFFIX = '@test.com';
export const LOADTEST_USER_PASSWORD = 'password123!';

export const ARTIST_ID_MIN = 1;
export const ARTIST_ID_MAX = 46;

export const YEAR = Number(__ENV.YEAR || 2026);
export const MONTH_MIN = 4;
export const MONTH_MAX = 12;

export const PATH_BY_ARTISTS = '/api/v1/schedule/by-artists';
export const PATH_FOLLOWING = '/api/v1/schedule/following';
export const PATH_LOGIN = '/api/v1/auth/login';
