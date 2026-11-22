import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = name => fs.readFileSync(path.join(root, name), 'utf8').replace(/^\uFEFF/, '');
const json = name => JSON.parse(read(name));
const specDir = 'docs/vtuber/v2.1';
const roles = json(`${specDir}/roles.json`);
const files = fs.readdirSync(path.join(root, specDir)).filter(name => name.endsWith('.txt'));
assert.equal(files.length, 28);
assert.equal(Object.keys(roles).length, 28);
const java = 'src/main/java/org/agmas/noellesroles';
const registry = read(`${java}/role/ModRoles.java`);
const declarations = [...registry.matchAll(/public static SRERole (\w+)\s*=([\s\S]*?);/g)]
  .filter(match => match[2].includes('.addFlag("vtuber")'));
assert.equal(declarations.length, 28);

let parsed = 0;
function checkJson(directory) {
  for (const entry of fs.readdirSync(path.join(root, directory), { withFileTypes: true })) {
    const name = `${directory}/${entry.name}`;
    if (entry.isDirectory()) checkJson(name);
    else if (entry.name.endsWith('.json')) { json(name); parsed++; }
  }
}
checkJson('src/main/resources');
const sourcePaths = ['role/vtuber', 'role_data/vtuber'].flatMap(directory =>
  fs.readdirSync(path.join(root, java, directory)).filter(name => name.endsWith('.java'))
    .map(name => `${java}/${directory}/${name}`));
sourcePaths.push(`${java}/game/roles/vtuber/VtuberRoleRuntime.java`);
const sources = sourcePaths.map(read).join('\n');
const keys = new Set([...sources.matchAll(/Component\.translatable\("([\w.]+)"/g)].map(match => match[1]));
for (const match of sources.matchAll(/RoleSkill\.skill\([^,]+,\s*"([\w.]+)"/g)) keys.add(match[1]);

for (const locale of ['zh_tw', 'zh_cn', 'en_us']) {
  const intro = json(`src/main/resources/assets/role_modifier_intro/lang/${locale}.json`);
  const merged = {};
  for (const namespace of fs.readdirSync(path.join(root, 'src/main/resources/assets'))) {
    const name = `src/main/resources/assets/${namespace}/lang/${locale}.json`;
    if (fs.existsSync(path.join(root, name))) Object.assign(merged, json(name));
  }
  for (const [name, id] of Object.entries(roles)) {
    const filename = files.find(file => file.endsWith(` ${name}.txt`));
    assert.ok(filename, name);
    let spec = read(`${specDir}/${filename}`).replace(/\r\n/g, '\n').trim();
    if (id === 'tinalis') spec = spec.replace('主動技2〖我的魔王大人！〗\nG鍵', '主動技2〖我的魔王大人！〗\nShift+G鍵');
    assert.equal(intro[`info.screen.roleid.${id}`], spec.replaceAll('%', '%%'), `${locale}: ${name} differs from v2.1`);
    assert.ok(intro[`info.screen.roleid.${id}.simple`]);
    assert.ok(merged[`announcement.star.role.${id}`], `${locale}: ${name} missing name`);
    assert.ok(merged[`announcement.star.goals.${id}`], `${locale}: ${name} missing goal`);
    assert.doesNotMatch(intro[`info.screen.roleid.${id}`], /(?<!%)%(?![%s]|\d+\$s)/);
  }
  for (const key of keys) if (!key.endsWith('.')) assert.ok(merged[key], `${locale}: missing ${key}`);
}
const events = read(`${java}/init/ModRolesInitialEventRegister.java`);
for (const match of declarations) {
  const name = match[1];
  const owner = /new (\w+Role)\(/.exec(match[2])?.[1];
  assert.ok(owner, name);
  const registrations = [...sources.matchAll(new RegExp(`RoleSkill\\.register\\(ModRoles\\.${name}\\s*,`, 'g'))];
  assert.ok(registrations.length <= 1, `${name}: duplicate skill registration`);
  if (registrations.length) assert.ok(events.includes(`${owner}.registerSkills();`), `${name}: skills not wired`);
}
console.log(`PASS: 28 v2.1 source documents, three-locale introductions/runtime keys, skill wiring, ${parsed} resource JSON files.`);
console.log('This checks authoring/resources; Minecraft multiplayer behavior still requires in-game verification.');
