"""Static authoring/resource checks. Does not replace Minecraft multiplayer testing."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/org/agmas/noellesroles'
RES = ROOT / 'src/main/resources/assets'
errors = []


def read(path):
    return path.read_text(encoding='utf-8-sig')


def require(ok, message):
    if not ok:
        errors.append(message)


registry = read(JAVA / 'role/ModRoles.java')
ids = dict(re.findall(r'ResourceLocation\s+(\w+)_ID\s*=\s*Noellesroles.id\("([^"]+)"\)', registry))
roles = {}
for match in re.finditer(r'public static SRERole (\w+)\s*=([\s\S]*?);', registry):
    name, declaration = match.groups()
    if '.addFlag("vtuber")' not in declaration:
        continue
    owner = re.search(r'new (\w+Role)\(', declaration)
    require(owner is not None, f'{name}: expected named role implementation')
    if owner:
        roles[name] = (ids[name], owner[1], declaration)
require(len(roles) == 27, f'Expected 27 VTuber roles, found {len(roles)}; update coverage when adding roles')

sources = [read(p) for p in (JAVA / 'role/vtuber').glob('*.java')]
sources += [read(p) for p in (JAVA / 'role_data/vtuber').glob('*.java')]
sources += [read(JAVA / 'game/roles/vtuber/VtuberRoleRuntime.java')]
joined = '\n'.join(sources)
events = read(JAVA / 'init/ModRolesInitialEventRegister.java')
for name, (role_id, owner, declaration) in roles.items():
    source = read(JAVA / f'role/vtuber/{owner}.java')
    require('setComponentKey' not in declaration, f'{name}: role-specific CCA binding remains')
    registrations = re.findall(r'RoleSkill\.register\(ModRoles\.' + name + r'\s*,', joined + '\n' + events)
    require(len(registrations) <= 1, f'{name}: duplicate unified skill registration')
    if registrations:
        require(f'{owner}.registerSkills();' in events, f'{name}: skill definitions are not wired')
    require('net.minecraft.client.' not in source and 'noellesroles.client.' not in source,
            f'{name}: common role references client-only classes')

for source in sources:
    require('ComponentKey<' not in source, 'VTuber-specific CCA found')
    require('sendParticles(' not in source, 'Inspect server particle calls against ParticleFx policy')

# The same namespace keys may live in any assets/*/lang file; Minecraft merges them.
literal_keys = set(re.findall(r'Component.translatable\("([\w.]+)"', joined))
literal_keys |= set(re.findall(r'RoleSkill.skill\([^,]+,\s*"([\w.]+)"', joined))
literal_keys = {k for k in literal_keys if not k.endswith('.')}
item_ids = ['alin_wrench', 'alin_screwdriver', 'yuyue_note', 'toy_hammer', 'fake_revolver', 'fake_knife']
for language in ['zh_cn', 'zh_tw', 'en_us']:
    merged = {}
    for path in RES.glob(f'*/lang/{language}.json'):
        merged.update(json.loads(read(path)))
    intro = json.loads(read(RES / f'role_modifier_intro/lang/{language}.json'))
    item_intro = json.loads(read(RES / f'item_intro/lang/{language}.json'))
    for name, (role_id, _, _) in roles.items():
        for key in [f'announcement.star.role.{role_id}', f'announcement.star.goals.{role_id}']:
            require(bool(merged.get(key)), f'{language}: missing {key}')
        for key in [f'info.screen.roleid.{role_id}', f'info.screen.roleid.{role_id}.simple']:
            require(bool(intro.get(key)), f'{language}: missing {key}')
            # Literal percentages must be escaped for the translatable component formatter.
            require(not re.search(r'(?<!%)%(?![%s]|\d+\$s)', intro.get(key, '')),
                    f'{language}: unescaped percent in {key}')
    for key in literal_keys:
        require(bool(merged.get(key)), f'{language}: missing runtime/skill key {key}')
    for item_id in item_ids:
        require(bool(merged.get(f'item.noellesroles.{item_id}')), f'{language}: missing item name {item_id}')
        require(bool(item_intro.get(f'item.noellesroles.{item_id}.desc')), f'{language}: missing item description {item_id}')

manifest = json.loads(read(ROOT / 'src/main/resources/fabric.mod.json'))
for old in ['HalicPlayerComponent', 'HakukoFoxPlayerComponent', 'NineMuiPlayerComponent',
            'EverlyPlayerComponent', 'FuTaiPlayerComponent', 'VtuberRolePlayerComponent']:
    for path in (ROOT / 'src/main').rglob('*.java'):
        require(old not in read(path), f'{path.relative_to(ROOT)}: stale reference {old}')

if errors:
    raise SystemExit('\n'.join(f'FAIL: {error}' for error in errors))
print(f'PASS: {len(roles)} VTuber roles; unique skills, RoleData boundaries, three-language resources and removed CCA references')
