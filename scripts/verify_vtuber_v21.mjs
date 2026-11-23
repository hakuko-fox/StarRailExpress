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

const ponderAssets = 'src/main/resources/assets/starrailexpress';
const ponderIndex = json(`${ponderAssets}/ponderer/content/role_tutorials.json`);
const locales = ['zh_tw', 'zh_cn', 'en_us'];
for (const id of Object.values(roles)) {
  const resource = `ponderer/scenes/role_tutorial_${id}.json`;
  const sceneId = `starrailexpress:role_tutorial.${id}`;
  assert.equal(ponderIndex.scenes.filter(entry => entry === `starrailexpress:${resource}`).length, 1,
    `${id}: expected exactly one Ponder scene index entry`);
  const scene = json(`${ponderAssets}/${resource}`);
  assert.equal(scene.id, sceneId, `${id}: Ponder scene ID must match the role path`);
  assert.ok(scene.items.length && scene.scenes.length, `${id}: empty Ponder scene`);
  assert.equal(new Set(scene.scenes.map(chapter => chapter.id)).size, scene.scenes.length,
    `${id}: duplicate Ponder chapter ID`);
  for (const locale of locales) assert.ok(scene.title[locale]?.trim(), `${id}: missing ${locale} title`);
  for (const chapter of scene.scenes) {
    const label = `${id}/${chapter.id}`;
    const stage = chapter.steps[0];
    assert.equal(stage?.type, 'show_structure', `${label}: missing opening structure`);
    assert.ok(scene.structures.includes(stage.structure), `${label}: undeclared structure`);
    const [namespace, structure] = stage.structure.split(':');
    assert.ok(fs.existsSync(path.join(root, 'src/main/resources/assets', namespace, 'ponder', `${structure}.nbt`)),
      `${label}: missing structure NBT`);
    const texts = chapter.steps.filter(step => step.type === 'text');
    assert.ok(texts.length, `${label}: missing tutorial text`);
    for (const locale of locales) {
      assert.ok(chapter.title[locale]?.trim(), `${label}: missing ${locale} chapter title`);
      for (const step of texts) assert.ok(step.text[locale]?.trim(), `${label}: missing ${locale} text`);
    }
  }
}

const sourcePaths = ['role/vtuber', 'role_data/vtuber'].flatMap(directory =>
  fs.readdirSync(path.join(root, java, directory)).filter(name => name.endsWith('.java'))
    .map(name => `${java}/${directory}/${name}`));
sourcePaths.push(`${java}/game/roles/vtuber/VtuberRoleRuntime.java`);
const sources = sourcePaths.map(read).join('\n');
const keys = new Set([...sources.matchAll(/Component\.translatable\("([\w.]+)"/g)].map(match => match[1]));
for (const match of sources.matchAll(/RoleSkill\.skill\([^,]+,\s*"([\w.]+)"/g)) keys.add(match[1]);

for (const locale of locales) {
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
console.log(`PASS: 28 v2.1 source documents and Ponder scenes, three-locale text/runtime keys, skill wiring, ${parsed} resource JSON files.`);
console.log('This checks authoring/resources; Minecraft multiplayer behavior still requires in-game verification.');
