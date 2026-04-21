import { ARTIST_ID_MAX, ARTIST_ID_MIN, MONTH_MAX, MONTH_MIN } from './config.js';

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function randomArtistIds(min = 2, max = 10) {
  const count = randomInt(min, max);
  const pool = [];
  for (let i = ARTIST_ID_MIN; i <= ARTIST_ID_MAX; i++) pool.push(i);
  for (let i = pool.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  return pool.slice(0, count);
}

export function randomMonth() {
  return randomInt(MONTH_MIN, MONTH_MAX);
}
