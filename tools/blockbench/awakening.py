"""Awakening scene: a player-shaped GeckoLib model (wide and slim arms) and its five animations.

One keyframe table produces both
  * the runtime files GeckoLib loads (assets/solostatus/geo/awakening/*.geo.json, animations/awakening/player.animation.json)
  * Blockbench projects (art/blockbench/awakening_player_*.bbmodel, GeckoLib "Animated Java/Entity" format) with a test skin,
so what you edit in Blockbench and what plays in the game are the same frames. Re-run after changing the table:
    python3 tools/blockbench/awakening.py
The model uses the standard 64x64 skin layout, so in game it is drawn with the player's own skin.
"""
import base64, io, json, math, os, uuid

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/solostatus')
ART = os.path.join(ROOT, 'art/blockbench')

# ----------------------------------------------------------------------------------------------- geometry
# Bedrock units (1 = one skin pixel), front of the model is north (-Z), y up, feet at y=0.

def box(origin, size, uv, inflate=0.0):
    c = {"origin": origin, "size": size, "uv": uv}
    if inflate:
        c["inflate"] = inflate
    return c

def eyelid(x, u):
    """A 2x1 px lid in front of an eye (eye row of a standard skin: face row 4). Its texture is the row above
    the eye (forehead/brow) of the same skin, so it takes the player's skin colour."""
    # 1.3 px tall (0.3 px below the eye row) and almost on the face: seen from above while lying, a lid of exactly
    # 1 px let a sliver of the eye white show under it (seen in the client end-to-end frames).
    return {"origin": [x, 26.7, -4.03], "size": [2, 1.3, 0.03], "uv": {"north": {"uv": [u, 11], "uv_size": [2, 1]}}}

def bones(slim):
    aw = 3 if slim else 4
    return [
        {"name": "root", "pivot": [0, 12, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 24, 0],
         "cubes": [box([-4, 12, -2], [8, 12, 4], [16, 16]), box([-4, 12, -2], [8, 12, 4], [16, 32], 0.25)]},
        {"name": "head", "parent": "root", "pivot": [0, 24, 0],
         "cubes": [box([-4, 24, -4], [8, 8, 8], [0, 0]), box([-4, 24, -4], [8, 8, 8], [32, 0], 0.5)]},
        # face image column 1-2 is on the model's +X side seen from the front, columns 5-6 on -X
        {"name": "lid_a", "parent": "head", "pivot": [2, 28, -4.03], "cubes": [eyelid(1, 9)]},
        {"name": "lid_b", "parent": "head", "pivot": [-2, 28, -4.03], "cubes": [eyelid(-3, 13)]},
        {"name": "right_arm", "parent": "root", "pivot": [-5, 22, 0],
         "cubes": [box([-4 - aw, 12, -2], [aw, 12, 4], [40, 16]), box([-4 - aw, 12, -2], [aw, 12, 4], [40, 32], 0.25)]},
        {"name": "left_arm", "parent": "root", "pivot": [5, 22, 0],
         "cubes": [box([4, 12, -2], [aw, 12, 4], [32, 48]), box([4, 12, -2], [aw, 12, 4], [48, 48], 0.25)]},
        {"name": "right_leg", "parent": "root", "pivot": [-1.9, 12, 0],
         "cubes": [box([-3.9, 0, -2], [4, 12, 4], [0, 16]), box([-3.9, 0, -2], [4, 12, 4], [0, 32], 0.25)]},
        {"name": "left_leg", "parent": "root", "pivot": [1.9, 12, 0],
         "cubes": [box([-0.1, 0, -2], [4, 12, 4], [16, 48]), box([-0.1, 0, -2], [4, 12, 4], [0, 48], 0.25)]},
    ]

def geo(slim):
    name = "slim" if slim else "wide"
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.solostatus.awakening_{name}", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 4, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones(slim)}]}

# ----------------------------------------------------------------------------------------------- animation
# GeckoLib rotation signs (checked in its BakedAnimationsAdapter): X and Y are negated, so
#   root X = -90 → lying on the back (face up, head toward +Z); head X > 0 → chin to chest;
#   legs X = -90 → legs forward (sitting); arms X > 0 → hands go back; right arm Z < 0 → arm out.
SEGMENTS = [("awakening.lie", 0.0, 1.5), ("awakening.open_eyes", 1.5, 2.5), ("awakening.raise_head", 2.5, 3.5),
            ("awakening.sit_up", 3.5, 5.5), ("awakening.stand_up", 5.5, 8.0)]
TOTAL = SEGMENTS[-1][2]

# (bone, channel) -> [(time, [x, y, z]), ...] in scene time; eased with smoothstep between keys
TRACKS = {
    ("root", "rotation"): [(0, [-90, 0, 0]), (3.5, [-90, 0, 0]), (5.0, [-12, 0, 0]), (5.5, [0, 0, 0]),
                           (6.3, [28, 0, 0]), (7.3, [8, 0, 0]), (8.0, [0, 0, 0])],
    ("root", "position"): [(0, [0, -10, 0]), (0.75, [0, -9.8, 0]), (1.5, [0, -10, 0]), (2.2, [0, -9.8, 0]), (3.0, [0, -10, 0]),
                           (5.5, [0, -10, 0]), (6.3, [0, -9, 0]), (7.3, [0, -1, 0]), (8.0, [0, 0, 0])],
    ("head", "rotation"): [(0, [0, 0, 0]), (2.6, [0, 0, 0]), (3.4, [38, 0, 0]), (3.5, [38, 0, 0]), (4.5, [18, 0, 0]),
                           (4.9, [10, -24, 0]), (5.3, [6, 22, 0]), (5.7, [0, 0, 0]), (6.3, [-12, 0, 0]), (8.0, [0, 0, 0])],
    ("lid_a", "scale"): [(0, [1, 1, 1]), (1.65, [1, 1, 1]), (1.85, [1, 0.45, 1]), (1.97, [1, 1, 1]), (2.15, [1, 0.25, 1]),
                         (2.35, [1, 0, 1]), (8.0, [1, 0, 1])],
    ("right_arm", "rotation"): [(0, [0, 0, -6]), (3.5, [0, 0, -6]), (4.2, [36, 0, -10]), (5.5, [18, 0, -4]),
                                (6.3, [-42, 0, -6]), (7.3, [-12, 0, -2]), (8.0, [0, 0, 0])],
    ("right_leg", "rotation"): [(0, [0, 0, 0]), (3.5, [0, 0, 0]), (5.0, [-80, 0, 2]), (5.5, [-88, 0, 4]),
                                (6.3, [-96, 0, 3]), (7.3, [-14, 0, 0]), (8.0, [0, 0, 0])],
}
# mirrored partners
TRACKS[("lid_b", "scale")] = TRACKS[("lid_a", "scale")]
TRACKS[("left_arm", "rotation")] = [(t, [v[0], v[1], -v[2]]) for t, v in TRACKS[("right_arm", "rotation")]]
TRACKS[("left_leg", "rotation")] = [(t, [v[0], v[1], -v[2]]) for t, v in TRACKS[("right_leg", "rotation")]]

def smooth(a):
    return a * a * (3 - 2 * a)

def value_at(track, t):
    if t <= track[0][0]:
        return track[0][1]
    for (t0, v0), (t1, v1) in zip(track, track[1:]):
        if t0 <= t <= t1:
            a = 0 if t1 == t0 else smooth((t - t0) / (t1 - t0))
            return [v0[i] + (v1[i] - v0[i]) * a for i in range(3)]
    return track[-1][1]

STEP = 0.1

def sample_times(start, end):
    n = int(round((end - start) / STEP))
    return [round(start + i * STEP, 4) for i in range(n)] + [end]

def r(v):
    return [round(x, 3) + 0.0 for x in v]

def animation_json():
    anims = {}
    for name, start, end in SEGMENTS:
        bones_out = {}
        for (bone, channel), track in TRACKS.items():
            keys = {}
            for t in sample_times(start, end):
                keys[f"{round(t - start, 3):.2f}"] = r(value_at(track, t))
            bones_out.setdefault(bone, {})[channel] = keys
        anims[name] = {"animation_length": round(end - start, 3), "bones": bones_out}
    return {"format_version": "1.8.0", "animations": anims, "geckolib_format_version": 2}

# ----------------------------------------------------------------------------------------------- test skin
def test_skin_png():
    """An original 64x64 test skin (not Mojang's) so the Blockbench preview has a face, eyes and clothes."""
    from PIL import Image
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    def fill(x0, y0, x1, y1, c):
        for x in range(x0, x1):
            for y in range(y0, y1):
                im.putpixel((x, y), c)
    skin, hair, shirt, pants, shoe = (226, 180, 146, 255), (52, 36, 30, 255), (36, 64, 112, 255), (40, 40, 52, 255), (24, 24, 28, 255)
    fill(0, 0, 32, 16, skin)
    fill(8, 0, 16, 8, hair); fill(0, 8, 32, 10, hair)       # top and the band above the face
    fill(8, 8, 16, 11, hair); fill(8, 11, 16, 12, skin)     # fringe, then brow row (lid texture)
    for x0 in (9, 13):                                      # eyes on face row 4 (v = 12)
        im.putpixel((x0, 12), (240, 240, 240, 255)); im.putpixel((x0 + 1, 12), (40, 70, 140, 255))
    fill(11, 14, 13, 15, (170, 110, 100, 255))              # mouth
    fill(16, 16, 40, 32, shirt); fill(40, 16, 56, 32, shirt); fill(32, 48, 48, 64, shirt)
    fill(44, 26, 48, 32, skin); fill(40, 28, 56, 32, skin)  # hands
    fill(36, 60, 40, 64, skin); fill(32, 60, 48, 64, skin)
    fill(0, 16, 16, 32, pants); fill(16, 48, 32, 64, pants)
    fill(0, 29, 16, 32, shoe); fill(16, 61, 32, 64, shoe)
    buf = io.BytesIO(); im.save(buf, 'PNG')
    return buf.getvalue()

# ----------------------------------------------------------------------------------------------- Blockbench project
def uid():
    return str(uuid.uuid4())

def box_faces(u, v, w, h, d):
    return {"east": {"uv": [u, v + d, u + d, v + d + h], "texture": 0}, "north": {"uv": [u + d, v + d, u + d + w, v + d + h], "texture": 0},
            "west": {"uv": [u + d + w, v + d, u + 2 * d + w, v + d + h], "texture": 0},
            "south": {"uv": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h], "texture": 0},
            "up": {"uv": [u + d + w, v + d, u + d, v], "texture": 0}, "down": {"uv": [u + 2 * w + d, v, u + d + w, v + d], "texture": 0}}

def bbmodel(slim, skin_png):
    name = "slim" if slim else "wide"
    elements, group_uuid, groups = [], {}, {}
    for b in bones(slim):
        gid = uid(); group_uuid[b["name"]] = gid
        groups[b["name"]] = {"name": b["name"], "origin": b["pivot"], "rotation": [0, 0, 0], "color": 0, "uuid": gid, "export": True,
                             "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": []}
        for i, c in enumerate(b.get("cubes", [])):
            eid = uid()
            o, s = c["origin"], c["size"]
            e = {"name": b["name"] + ("_layer" if i else ""), "rescale": False, "locked": False, "render_order": "default",
                 "allow_mirror_modeling": True, "from": o, "to": [o[k] + s[k] for k in range(3)], "autouv": 0, "color": i,
                 "inflate": c.get("inflate", 0), "origin": b["pivot"], "type": "cube", "uuid": eid}
            if isinstance(c["uv"], list):
                e["box_uv"] = True; e["uv_offset"] = c["uv"]; e["faces"] = box_faces(c["uv"][0], c["uv"][1], s[0], s[1], s[2])
            else:
                n = c["uv"]["north"]; u, v = n["uv"]; w, h = n["uv_size"]
                e["box_uv"] = False
                e["faces"] = {f: {"uv": [0, 0, 0, 0], "texture": None} for f in ("east", "west", "south", "up", "down")}
                e["faces"]["north"] = {"uv": [u, v, u + w, v + h], "texture": 0}
            elements.append(e)
            groups[b["name"]]["children"].append(eid)
    outliner = []
    for b in bones(slim):
        (groups[b["parent"]]["children"] if "parent" in b else outliner).append(groups[b["name"]])
    anims = []
    data = animation_json()["animations"]
    for name_, a in data.items():
        animators = {}
        for bone, channels in a["bones"].items():
            kfs = []
            for channel, keys in channels.items():
                for t, v in keys.items():
                    kfs.append({"channel": channel, "data_points": [{"x": str(v[0]), "y": str(v[1]), "z": str(v[2])}], "uuid": uid(),
                                "time": float(t), "color": -1, "interpolation": "linear"})
            animators[group_uuid[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
        anims.append({"uuid": uid(), "name": name_, "loop": "once", "override": False, "length": a["animation_length"], "snapping": 20,
                      "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators})
    return {"meta": {"format_version": "4.10", "model_format": "animated_entity_model", "box_uv": True},
            "name": f"awakening_player_{name}", "model_identifier": "", "visible_box": [4, 3, 1], "variable_placeholders": "",
            "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
            "resolution": {"width": 64, "height": 64}, "elements": elements, "outliner": outliner,
            "textures": [{"path": "", "name": "test_skin.png", "folder": "", "namespace": "", "id": "0", "width": 64, "height": 64,
                          "uv_width": 64, "uv_height": 64, "particle": False, "render_mode": "default", "visible": True, "mode": "bitmap",
                          "saved": True, "uuid": uid(), "relative_path": "test_skin.png",
                          "source": "data:image/png;base64," + base64.b64encode(skin_png).decode()}],
            "animations": anims}

# ----------------------------------------------------------------------------------------------- checks
def validate():
    """What the game relies on: every animated bone exists, segments join without jumps, eyes end open."""
    names = {b["name"] for b in bones(False)}
    assert names == {b["name"] for b in bones(True)}
    anim = animation_json()["animations"]
    assert [s[0] for s in SEGMENTS] == list(anim)
    for (bone, _), _t in TRACKS.items():
        assert bone in names, bone
    for (n0, _, _), (n1, _, _) in zip(SEGMENTS, SEGMENTS[1:]):
        for bone, chans in anim[n0]["bones"].items():
            for ch, keys in chans.items():
                end = list(keys.values())[-1]; start = list(anim[n1]["bones"][bone][ch].values())[0]
                assert end == start, (n0, n1, bone, ch, end, start)
    assert value_at(TRACKS[("lid_a", "scale")], TOTAL)[1] == 0, "eyes must end open"
    assert value_at(TRACKS[("root", "rotation")], 0)[0] == -90 and value_at(TRACKS[("root", "position")], TOTAL) == [0, 0, 0]
    return True

def main():
    validate()
    os.makedirs(os.path.join(ASSETS, 'geo/awakening'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'animations/awakening'), exist_ok=True)
    os.makedirs(ART, exist_ok=True)
    for slim in (False, True):
        name = "slim" if slim else "wide"
        with open(os.path.join(ASSETS, f'geo/awakening/player_{name}.geo.json'), 'w') as f:
            json.dump(geo(slim), f, indent=1)
    with open(os.path.join(ASSETS, 'animations/awakening/player.animation.json'), 'w') as f:
        json.dump(animation_json(), f, indent=1)
    skin = test_skin_png()
    with open(os.path.join(ART, 'test_skin.png'), 'wb') as f:
        f.write(skin)
    for slim in (False, True):
        with open(os.path.join(ART, f'awakening_player_{"slim" if slim else "wide"}.bbmodel'), 'w') as f:
            json.dump(bbmodel(slim, skin), f, indent=1)
    print('awakening: models, animations and Blockbench projects written; checks passed')

if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['--check']:
        validate(); print('awakening: checks passed')
    else:
        main()
