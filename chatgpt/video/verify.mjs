import { readFileSync, existsSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { parseEnv } from '../config.mjs';

const dir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(dir, '../..');
const ffmpeg = process.env.FFMPEG || path.join(root, '.runtime/media/node_modules/@ffmpeg-installer/win32-x64/ffmpeg.exe');
const ffprobe = process.env.FFPROBE || path.join(root, '.runtime/media/node_modules/@ffprobe-installer/win32-x64/ffprobe.exe');
const movie = path.join(dir, 'oracle-chatgpt-walkthrough.mp4');
for (const file of ['oracle-chatgpt-walkthrough.mp4', 'oracle-chatgpt-walkthrough-web.mp4', 'poster.png', 'captions.srt', 'captions.vtt', 'narration.txt', 'timing.json']) assert.ok(existsSync(path.join(dir, file)), `Missing ${file}`);
const probe = spawnSync(ffprobe, ['-v', 'error', '-show_format', '-show_streams', '-of', 'json', movie], { encoding: 'utf8' });
assert.equal(probe.status, 0);
const metadata = JSON.parse(probe.stdout);
const video = metadata.streams.find(stream => stream.codec_type === 'video');
const audio = metadata.streams.find(stream => stream.codec_type === 'audio');
assert.equal(video.width, 1920); assert.equal(video.height, 1080);
assert.equal(video.codec_name, 'h264'); assert.equal(audio.codec_name, 'aac');
const timing = JSON.parse(readFileSync(path.join(dir, 'timing.json'), 'utf8').replace(/^\uFEFF/, ''));
const expected = timing.reduce((sum, scene) => sum + scene.duration, 0);
assert.ok(Math.abs(Number(metadata.format.duration) - expected) < 1);
const captions = readFileSync(path.join(dir, 'captions.srt'), 'utf8');
const blocks = captions.trim().split(/\r?\n\r?\n/);
for (const [index, block] of blocks.entries()) {
  const lines = block.split(/\r?\n/);
  assert.equal(Number(lines[0]), index + 1);
  assert.match(lines[1], /^\d{2}:\d{2}:\d{2},\d{3} --> \d{2}:\d{2}:\d{2},\d{3}$/);
  assert.ok(lines.length <= 4); assert.ok(lines.slice(2).every(line => line.length <= 42));
}
const blog = readFileSync(path.join(dir, '../blog.html'), 'utf8');
for (const value of ['<video controls', '<source src="video/oracle-chatgpt-walkthrough-web.mp4"', 'video/oracle-chatgpt-walkthrough.mp4', 'video/poster.png', 'video/captions.vtt', 'kind="captions"']) assert.ok(blog.includes(value));
const secrets = Object.entries(parseEnv(readFileSync(path.join(root, '.env'), 'utf8'), { includeTunnelKey: true }))
  .filter(([key, value]) => /PASSWORD|TOKEN|SECRET|(?:^|_)KEY(?:_|$)/.test(key) && value.length >= 8).map(([, value]) => value);
for (const file of ['captions.srt', 'captions.vtt', 'narration.txt', 'scenes.json', '../blog.html', '../IMPLEMENTATION.md']) {
  const text = readFileSync(path.join(dir, file), 'utf8');
  assert.ok(secrets.every(secret => !text.includes(secret)), `Credential value found in ${file}`);
}
// Decode every video and audio packet, not just a poster or selected frame.
const decode = spawnSync(ffmpeg, ['-hide_banner', '-v', 'error', '-i', movie, '-f', 'null', '-'], { encoding: 'utf8', timeout: 180000 });
assert.equal(decode.status, 0, 'Complete MP4 decode failed');
assert.equal(decode.stderr.trim(), '');
const report = { dimensions: '1920x1080', durationSeconds: Number(metadata.format.duration), videoCodec: video.codec_name, audioCodec: audio.codec_name, scenes: timing.length, captions: blocks.length, completeDecode: 'passed', blogLinks: 'passed', textCredentialScan: 'passed' };
writeFileSync(path.join(dir, 'verification.json'), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
