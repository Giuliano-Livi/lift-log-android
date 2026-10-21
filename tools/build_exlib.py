"""Build the exercise library (web/exlib.js) and the exercise drawings (web/ex/*.webp)
from the Everkinetic open exercise data.

Source: https://github.com/everkinetic/data, exercises and drawings by Greg Priday / Everkinetic,
licensed CC BY-SA 4.0. The drawings are adapted here: cropped, resized, recoloured to light lines on a
transparent background and the two positions placed side by side. The adapted drawings in web/ex/ are
shared under the same licence (CC BY-SA 4.0).

Usage:
    git clone --depth 1 https://github.com/everkinetic/data /tmp/everkinetic
    python3 tools/build_exlib.py /tmp/everkinetic [--force]

Drawings that already exist in web/ex/ are kept unless --force is given (converting all of them takes minutes).
"""
import json
import os
import re
import sys

from PIL import Image, ImageOps

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_JS = os.path.join(ROOT, 'web', 'exlib.js')
OUT_IMG = os.path.join(ROOT, 'web', 'ex')
FRAME = 256

# Exercises already in the built-in plan (and their alternatives) -> matching Everkinetic drawing.
# Exercises without a close match keep the app's own pictogram.
MAP = {
    'incline-bb': 'incline-bench-press', 'incline-db': 'incline-dumbbell-press',
    'smith-incline': 'smith-machine-incline-bench-press', 'machine-bench': 'machine-bench-press',
    'db-bench': 'bench-press-dumbbell', 'bb-bench': 'bench-press',
    'machine-shoulder': 'seated-shoulder-press-machine', 'db-shoulder': 'dumbbell-shoulder-press',
    'smith-ohp': 'seated-military-press',
    'cable-1arm-lateral': 'lateral-dumbbell-raises', 'cable-lateral': 'lateral-dumbbell-raises',
    'db-lateral': 'lateral-dumbbell-raises', 'machine-lateral': 'lateral-dumbbell-raises',
    'oh-tricep-ext': 'standing-triceps-extension', 'db-oh-ext': 'seated-two-arm-triceps-extension-with-dumbbell',
    'skull': 'lying-triceps-press-with-barbell',
    'cable-crunch': 'seated-ab-crunch-with-cable', 'machine-crunch': 'seated-ab-crunch-with-cable',
    'decline-crunch': 'decline-crunch',
    'pull-up': 'pull-ups', 'neutral-pullup': 'narrow-parallel-grip-chin-ups', 'lat-pd-wide': 'wide-grip-lat-pull-down',
    'bb-row': 'reverse-grips-bent-over-barbell-rows', 'tbar-row': 't-bar-rows',
    'lat-rev': 'underhand-pull-down', 'close-pd': 'v-bar-pull-down',
    'prone-lateral': 'lying-rear-lateral-raise', 'reverse-fly': 'seated-rear-lateral-cable-raise',
    'bb-curl': 'biceps-curls-with-barbell', 'ez-curl': 'ez-bar-curl-with-barbell',
    'db-curl': 'biceps-curl-with-dumbbell', 'hammer-curl': 'biceps-hammer-curl-with-dumbbell',
    'squat': 'barbell-squat', 'smith-squat': 'smith-machine-squats', 'leg-press': 'leg-press',
    'rdl': 'romanian-dead-lift', 'db-rdl': 'dumbbell-dead-lifts', 'back-ext': 'hyperextensions',
    'hack': 'hack-squat-machine', 'bulgarian': 'single-leg-squat-with-barbell',
    'leg-curl': 'seated-leg-curl', 'lying-curl': 'lying-leg-curl-machine', 'standing-curl': 'standing-leg-curls',
    'leg-ext': 'leg-extensions', 'sissy': 'weighted-sissy-squat-with-weight-plate', 'goblet': 'squats-using-dumbbells',
    'seated-calf': 'seated-calf-raise-using-machine', 'leg-press-calf': 'calves-press-on-leg-machine',
    'standing-calf': 'standing-calf-raises-using-machine', 'smith-calf': 'standing-calf-raises-using-machine',
    'ab-wheel': 'ab-rollout-on-knees-with-barbell', 'reverse-crunch': 'bent-knee-hip-raise',
    'lat-pd': 'wide-grip-lat-pull-down', 'straight-arm': 'straight-arm-push-down',
    'cable-row': 'seated-cable-rows', 'machine-row': 'seated-cable-rows',
    'machine-fly': 'butterfly-machine', 'cable-fly': 'cable-crossover', 'db-fly': 'dumbbell-flys',
    'dip': 'chest-dips', 'close-bench': 'close-grip-barbell-bench-press',
    'pushdown': 'triceps-pushdown-with-rope-and-cable', 'bar-pushdown': 'triceps-pushdown-with-cable',
    'kickback': 'triceps-kickback-with-dumbbell',
    'preacher': 'preacher-curl-with-machine', 'db-preacher': 'one-arm-preacher-curl-with-dumbbell',
    'cable-curl': 'standing-biceps-curl-with-cable',
}

MUSCLE = {
    'chest': 'chest', 'triceps': 'triceps', 'biceps': 'biceps', 'arms': 'biceps', 'quadriceps': 'quads',
    'hamstrings': 'hams', 'hamstring': 'hams', 'calves': 'calves', 'abdominals': 'abs', 'lower abdominals': 'abs',
    'obliques': 'abs', 'core': 'abs', 'lats': 'back', 'middle back': 'back', 'back': 'back', 'trapezius': 'back',
    'lower back': 'back', 'gluts': 'glutes', 'lateral deltoid': 'sidedelt', 'rear deltoid': 'reardelt',
    'posterior deltoid': 'reardelt',
}
EQUIP = {
    'barbell': 'barbell', 'barbell or dumbbell': 'barbell', 'dumbbell': 'dumbbell', 'dumbbells': 'dumbbell',
    'dumbell': 'dumbbell', 'body': 'bodyweight', 'bar': 'bodyweight', 'parallel bars': 'bodyweight',
    'cable': 'cable', 'cable machine': 'cable', 'v-bar': 'cable', 'machine': 'machine', 'chest machine': 'machine',
    'butterfly machine': 'machine', 'bench press machine': 'machine', 't-bar machine': 'machine',
    'hyperextension bench': 'machine', 'smith machine': 'smith', 'exercise band': 'band', 'bands': 'band',
    'exercise ball': 'ball', 'swiss ball': 'ball', 'bosu ball': 'ball', 'medicine ball': 'ball',
    'weight plate': 'plate', 'weight': 'plate',
}
LEGS = re.compile(r'squat|leg press|dead ?lift|hack|lunge|calf|calves|good morning', re.I)
# muscle from the name, for the few exercises the source lists without one
BY_NAME = [(re.compile(r'bench press|push ?up|fly', re.I), 'chest'), (re.compile(r'dead ?lift', re.I), 'hams'),
           (re.compile(r'row|pull ?up|chin ?up|pull ?down', re.I), 'back'), (re.compile(r'curl', re.I), 'biceps')]


def muscles(e):
    name = e['title'].lower()
    out = []
    for part in (e.get('primary') or '').lower().split(','):
        p = part.strip()
        if not p:
            continue
        if p == 'shoulders':
            if re.search(r'rear|back fly|bent[- ]over lateral|reverse fl', name):
                m = 'reardelt'
            elif 'lateral' in name or 'upright' in name:
                m = 'sidedelt'
            else:
                m = 'shoulders'
        else:
            m = MUSCLE.get(p)
        if m and m not in out:
            out.append(m)
    if not out:
        for pat, m in BY_NAME:
            if pat.search(name):
                out.append(m)
                break
    return out


def equipment(e):
    out = []
    for x in e.get('equipment') or []:
        m = EQUIP.get(x.lower().strip())
        if 'barbell or dumbbell' == x.lower():
            for y in ('barbell', 'dumbbell'):
                if y not in out:
                    out.append(y)
        elif m and m not in out:
            out.append(m)
    return out or ['bodyweight']


def frames(base, e):
    a = os.path.join(base, 'dist', 'png', e['id_num'] + '-relaxation.png')
    b = os.path.join(base, 'dist', 'png', e['id_num'] + '-tension.png')
    if os.path.exists(a) and os.path.exists(b):
        return [a, b]
    web = [os.path.join(base, 'src', 'images-web', os.path.basename(p)) for p in (e.get('img') or [])[:2]]
    if len(web) == 2 and all(os.path.exists(p) for p in web):
        return web
    return None


def line_art(path):
    """Light lines on a transparent square, so the drawing sits on the app's dark tiles."""
    im = Image.open(path).convert('RGBA')
    bg = Image.new('RGBA', im.size, (255, 255, 255, 255))
    bg.alpha_composite(im)
    gray = ImageOps.grayscale(bg)
    ink = ImageOps.invert(gray).point(lambda v: 0 if v < 18 else min(255, int(v * 1.25)))
    box = ink.getbbox()
    if box:
        pad = 12
        box = (max(0, box[0] - pad), max(0, box[1] - pad), min(ink.width, box[2] + pad), min(ink.height, box[3] + pad))
        ink = ink.crop(box)
    ink.thumbnail((FRAME, FRAME), Image.LANCZOS)
    tile = Image.new('L', (FRAME, FRAME), 0)
    tile.paste(ink, ((FRAME - ink.width) // 2, (FRAME - ink.height) // 2))
    return tile


def main(base, force=False):
    data = json.load(open(os.path.join(base, 'exercises.json')))
    os.makedirs(OUT_IMG, exist_ok=True)
    mapped = set(MAP.values())
    items, seen = [], {}
    for e in sorted(data, key=lambda x: x['title']):
        fr = frames(base, e)
        slug = e['name']
        dup = seen.get(e['title'].strip().lower()) or next((i for i in items if i['k'] == slug), None)
        if dup is not None:
            # Everkinetic lists a few exercises twice; keep the one that has drawings
            if dup['img'] or not fr:
                continue
            items.remove(dup)
        eq = equipment(e)
        weighted = [x for x in eq if x not in ('bodyweight', 'band', 'ball')]
        legs = bool(LEGS.search(e['title']))
        inc = 5 if legs and any(x in eq for x in ('barbell', 'machine', 'smith')) else 2 if eq == ['dumbbell'] or 'dumbbell' in eq and len(weighted) == 1 else 2.5
        item = {
            'k': slug,
            'n': e['title'].replace('Fly’s', 'Flys').replace("Fly's", 'Flys').strip(),
            'm': muscles(e),
            'eq': eq,
            't': 'compound' if 'compound' in (e.get('type') or '') else 'isolation',
            'bw': not weighted,
            'inc': inc,
            'st': [s.strip() for s in e.get('steps') or [] if s.strip()],
            'tips': [s.strip() for s in e.get('tips') or [] if s.strip()],
            'img': 0,
            'used': 1 if slug in mapped else 0,
        }
        if fr:
            out = os.path.join(OUT_IMG, slug + '.webp')
            if force or not os.path.exists(out):
                sprite = Image.new('RGBA', (FRAME * 2, FRAME), (242, 243, 249, 0))
                for i, p in enumerate(fr):
                    alpha = line_art(p)
                    layer = Image.new('RGBA', (FRAME, FRAME), (242, 243, 249, 255))
                    layer.putalpha(alpha)
                    sprite.alpha_composite(layer, (i * FRAME, 0))
                sprite.save(out, 'WEBP', quality=80, method=6)
            item['img'] = 1
        items.append(item)
        seen[e['title'].strip().lower()] = item
    keep = set(i['k'] + '.webp' for i in items if i['img'])
    for f in os.listdir(OUT_IMG):
        if f not in keep:
            os.remove(os.path.join(OUT_IMG, f))
    missing = [k for k, v in MAP.items() if not any(i['k'] == v and i['img'] for i in items)]
    if missing:
        raise SystemExit('mapped exercises without a drawing: %s' % missing)
    lib = {
        'credit': 'Exercise drawings and instructions: Everkinetic (everkinetic.com) by Greg Priday, '
                  'CC BY-SA 4.0. Drawings adapted for Lift Log (cropped, recoloured, positions side by side).',
        'license': 'https://creativecommons.org/licenses/by-sa/4.0/',
        'source': 'https://github.com/everkinetic/data',
        'map': MAP,
        'items': items,
    }
    with open(OUT_JS, 'w', encoding='utf-8') as fh:
        fh.write('/* Lift Log exercise library. ' + lib['credit'] + ' ' + lib['license'] + ' */\n')
        fh.write('window.EXLIB = ' + json.dumps(lib, ensure_ascii=False, separators=(',', ':')) + ';\n')
    total = sum(os.path.getsize(os.path.join(OUT_IMG, f)) for f in os.listdir(OUT_IMG))
    print('%d exercises, %d drawings (%.1f MB), exlib.js %.0f KB' % (
        len(items), len(os.listdir(OUT_IMG)), total / 1e6, os.path.getsize(OUT_JS) / 1e3))


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if a != '--force']
    if len(args) != 1:
        raise SystemExit(__doc__)
    main(args[0], '--force' in sys.argv[1:])
