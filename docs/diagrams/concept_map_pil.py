from PIL import Image, ImageDraw, ImageFont
import math, os

S = 2  # supersample
W, H = 1900 * S, 1400 * S
im = Image.new('RGB', (W, H), 'white'); d = ImageDraw.Draw(im)
F = ImageFont.truetype('arial.ttf', 19 * S); FB = ImageFont.truetype('arialbd.ttf', 18 * S); FS = ImageFont.truetype('arial.ttf', 15 * S)
FL = ImageFont.truetype('ariali.ttf', 17 * S)

boxes = {}
def box(key, x, y, w, h, title, lines=(), fill='#f2f2f2', edge='#555', tf=FB):
    h = 34 + 19 * len(lines) + 8
    x, y, w, h = x * S, y * S, w * S, h * S
    d.rounded_rectangle([x, y, x + w, y + h], radius=10 * S, fill=fill, outline=edge, width=2 * S)
    cy = y + 14 * S
    d.text((x + w / 2, cy + 10 * S), title, font=tf, fill='#111', anchor='mm'); cy += 26 * S
    for l in lines:
        d.text((x + w / 2, cy + 8 * S), l, font=FS, fill='#222', anchor='mm'); cy += 19 * S
    boxes[key] = (x, y, w, h)

def edge(a, b, label='', side_a='b', side_b='t', dotted=False, via=None, lab_off=(0, 0)):
    ax, ay, aw, ah = boxes[a]; bx, by, bw, bh = boxes[b]
    def pt(bx_, by_, bw_, bh_, s):
        return {'t': (bx_ + bw_ / 2, by_), 'b': (bx_ + bw_ / 2, by_ + bh_), 'l': (bx_, by_ + bh_ / 2), 'r': (bx_ + bw_, by_ + bh_ / 2)}[s]
    p1 = pt(ax, ay, aw, ah, side_a); p2 = pt(bx, by, bw, bh, side_b)
    pts = [p1] + [(v[0] * S, v[1] * S) for v in (via or [])] + [p2]
    col = '#444'
    for i in range(len(pts) - 1):
        x1, y1 = pts[i]; x2, y2 = pts[i + 1]
        if dotted:
            n = int(math.hypot(x2 - x1, y2 - y1) / (10 * S))
            for k in range(0, n, 2):
                t0, t1 = k / n, min((k + 1) / n, 1)
                d.line([x1 + (x2 - x1) * t0, y1 + (y2 - y1) * t0, x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1], fill=col, width=2 * S)
        else:
            d.line([x1, y1, x2, y2], fill=col, width=2 * S)
    x1, y1 = pts[-2]; x2, y2 = pts[-1]
    a = math.atan2(y2 - y1, x2 - x1)
    d.polygon([(x2, y2), (x2 - 12 * S * math.cos(a - .4), y2 - 12 * S * math.sin(a - .4)), (x2 - 12 * S * math.cos(a + .4), y2 - 12 * S * math.sin(a + .4))], fill=col)
    if label:
        mid = pts[len(pts) // 2] if len(pts) > 2 else ((p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2)
        mx, my = mid[0] + lab_off[0] * S, mid[1] + lab_off[1] * S
        tw = d.textlength(label, font=FL)
        d.rectangle([mx - tw / 2 - 4 * S, my - 11 * S, mx + tw / 2 + 4 * S, my + 11 * S], fill='#e6e6e6')
        d.text((mx, my), label, font=FL, fill='#222', anchor='mm')

def band(y, h, title, fill):
    d.rectangle([20 * S, y * S, (1900 - 20) * S, (y + h) * S], fill=fill, outline='#ccc', width=1 * S)
    d.text((32 * S, (y + 6) * S), title, font=FB, fill='#666')

# ---------------- Row 1: problem framing
band(20, 330, 'PROBLEM CONTEXT', '#fbfbfb')
box('adhd', 40, 60, 230, 70, 'ADHD', ['children, adolescents, adults'])
box('mgmt', 290, 60, 250, 70, 'Ongoing management', ['behavioural strategies,', 'digital tasks, games'])
box('clinic', 570, 60, 220, 70, 'Brief, periodic', ['in-clinic observation'])
box('recall', 840, 60, 220, 70, 'Retrospective recall', ['self / caregiver'])
box('guess', 1110, 60, 220, 70, 'Content chosen', ['by guesswork'])
box('eyetr', 545, 210, 275, 70, 'Existing device-camera', ['eye-tracking research'])
box('diag', 840, 210, 220, 70, 'Diagnostic', ['classification'])
box('gap', 1110, 200, 300, 90, 'RESEARCH GAP', ['actionable, per-content attention', 'feedback for the individual'], fill='#fde9d9', edge='#c0504d')
box('ff', 1480, 200, 240, 90, 'FocusFlow', ['on-device gaze-based', 'attention profiling'], fill='#dbe5f1', edge='#1f497d')
box('not', 1480, 60, 240, 70, 'Not a diagnostic', ['or screening tool'], fill='#eaf1dd', edge='#4f6228')

edge('adhd', 'mgmt', 'requires', 'r', 'l')
edge('mgmt', 'clinic', 'relies on', 'r', 'l')
edge('clinic', 'recall', 'informed by', 'r', 'l')
edge('recall', 'guess', 'subjective', 'r', 'l')
edge('guess', 'gap', 'motivates', 'b', 't')
edge('eyetr', 'diag', 'focuses on', 'r', 'l')
edge('diag', 'gap', 'leaves', 'r', 'l')
edge('gap', 'ff', 'addressed by', 'r', 'l')
edge('ff', 'not', 'constrained', 't', 'b', dotted=True)

# ---------------- Row 2: sensing pipeline
band(380, 400, 'ON-DEVICE SENSING AND PER-FRAME ATTENTION DECISION', '#f4f7fb')
box('cam', 40, 430, 220, 80, 'Front-facing camera', ['CameraX ImageAnalysis'])
box('mp', 300, 430, 240, 80, 'MediaPipe', ['Face Landmarker'])
box('sig', 570, 430, 290, 80, 'Per-frame signals', ['478 landmarks, 10 iris points,', 'gaze blendshapes, head yaw / pitch'])
box('dec', 875, 430, 275, 80, 'Screen-attention decision', ['gaze deflection + head pose'])
box('temp', 1160, 430, 280, 100, 'Temporal layer', ['screen-attention %, gaze shifts,', 'blink count, first sustained', 'lapse (rolling window)'])
box('early', 1160, 590, 280, 70, 'Sustained drop', ['clip ends early; not successful'], fill='#fde9d9', edge='#c0504d')
box('priv', 300, 590, 540, 90, 'PRIVACY BOUNDARY', ['frames processed in memory and discarded;', 'no frame, landmark or per-frame value stored', 'or transmitted — only derived metrics kept'], fill='#eaf1dd', edge='#4f6228')
box('stim', 1480, 430, 240, 100, 'Curated stimulus library', ['9 categories, fixed verified ids,', 'randomized order,', 'per-clip duration cap'])
box('rate', 1480, 590, 240, 70, 'Two post-clip', ['self-ratings (1–5)'])

edge('ff', 'cam', 'senses via', 'l', 't', via=[(1460, 245), (1460, 350), (150, 350)], lab_off=(0, 0))
edge('ff', 'stim', 'presents', 'b', 't')
edge('cam', 'mp', '', 'r', 'l')
edge('mp', 'sig', 'yields', 'r', 'l')
edge('sig', 'dec', 'per frame', 'r', 'l')
edge('dec', 'temp', 'over time', 'r', 'l')
edge('temp', 'early', '', 'b', 't')
edge('mp', 'priv', '', 'b', 't', dotted=True)
edge('stim', 'rate', 'after each clip', 'b', 't')

# ---------------- Row 3: scoring → profile → recommendations
band(830, 520, 'SCORING, SESSION RULE, PROFILE AND RECOMMENDATIONS', '#f7fbf4')
box('score', 40, 890, 360, 130, 'Per-category attention score 0–100', ['weighted combination of:', 'screen attention · distraction delay ·', 'gaze stability · blink consistency ·', 'interest · focus', '(self-report weight redistributed if absent)'])
box('rule', 430, 890, 280, 100, 'Session rule', ['profile only after 5 successful', 'assessments; bounded skips;', 'graceful exhaustion'])
box('prof', 760, 890, 240, 80, 'Attentional profile', ['ranked categories'])
box('trait', 1050, 890, 260, 100, 'Trait-cluster aggregation', ['auditory · visual · interactive ·', 'narrative · textual', '(weighted by score)'])
box('ls', 1360, 890, 240, 100, 'Learning style', ['auditory · visual · interactive ·', 'narrative · structured ·', 'multimodal'])
box('rec', 1360, 1080, 240, 90, 'Recommendations', ['study techniques,', 'content formats,', 'weekly goals'])
box('dash', 1050, 1080, 280, 90, 'Dashboard and PDF report', ['ranking, scores, style,', 'provisional status,', 'progress over time'])
box('local', 430, 1080, 280, 90, 'Local persistence (Room)', ['derived metrics only:', 'scores, sub-scores, frame rate,', 'quality flags'])
box('remote', 40, 1080, 360, 90, 'Managed backend (Supabase)', ['authentication, profile record,', 'optional diagnosis document —', 'never camera or gaze data'])
box('sec', 750, 1080, 260, 90, 'Educators / clinicians', ['observational context only,', 'shared if the user', 'chooses to'])
box('home', 40, 1230, 360, 80, 'Personalised, evidence-based', ['study habits at home'], fill='#dbe5f1', edge='#1f497d')

edge('temp', 'score', 'feeds', 'b', 't', via=[(1300, 800), (210, 800)])
edge('rate', 'score', '', 'b', 't', via=[(1600, 780), (250, 780)])
edge('score', 'rule', 'requires', 'r', 'l')
edge('rule', 'prof', 'releases', 'r', 'l')
edge('prof', 'trait', 'aggregates', 'r', 'l')
edge('trait', 'ls', 'infers', 'r', 'l')
edge('ls', 'rec', 'generates', 'b', 't')
edge('rec', 'dash', 'shown on', 'l', 'r')
edge('dash', 'sec', 'optionally shared', 'l', 'r')
edge('score', 'local', 'stored', 'b', 't', via=[(210, 1050), (570, 1050)])
edge('dash', 'home', 'informs', 'b', 't', via=[(1180, 1220), (210, 1220)])
edge('home', 'mgmt', 'improves', 'l', 't', via=[(28, 1265), (28, 10), (415, 10)], lab_off=(0, 640))

im = im.resize((W // S, H // S), Image.LANCZOS)
im.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), '1_concept_map.png'))
print('ok', im.size)
