#!/usr/bin/env python3
"""Bake a PixelGen 1.0 scene export into a Chemopunk asset.

Usage:
    python tools/pixelgen_bake.py <scene.json> <output.json>

Input: a PixelGen 1.0 scene export (e.g. the JSON produced by
pixelgen-v1.0.0-stable render/export tooling, see the Implements repo).
Output: a compact "chemopunk-pixelgen-scene/1" asset loadable by
com.example.data.PixelGenSceneLoader from app assets.

The mapping is intentionally explicit and total:
    collision solid  -> s -> TileType.WALL
    collision walk   -> w -> TileType.FLOOR
    collision hazard -> h -> TileType.TOXIC_POOL
Object kinds not in INTERACTIVE_MAP are decorative in PixelGen and are
dropped, exactly like the hand-baked seed asset.
"""

import json
import sys

FORMAT = "chemopunk-pixelgen-scene/1"

COLLISION_CHARS = {"solid": "s", "walk": "w", "hazard": "h"}

# PixelGen scene object kinds that have gameplay meaning in Chemopunk.
# Everything else (cliffs, stakes, growths, backdrop props) is decorative.
INTERACTIVE_MAP = {
    "printing_press_altar": "TERMINAL",
    "warning_board": "TERMINAL",
    "nanolith_shrine": "BEACON",
    "mask_cross": "SWITCH",
    "brazier_lit": "SWITCH",
    "biogel_barrel_leaking": "LOCKER",
}


def bake(scene):
    collision = scene["collision"]
    height = scene["height"]
    width = scene["width"]
    if len(collision) != height:
        raise SystemExit("scene height %d does not match %d collision rows" % (height, len(collision)))
    rows = []
    counts = {}
    for row in collision:
        if len(row) != width:
            raise SystemExit("collision row has %d entries, expected %d" % (len(row), width))
        chars = []
        for token in row:
            if token not in COLLISION_CHARS:
                raise SystemExit("unknown collision token: %r" % token)
            chars.append(COLLISION_CHARS[token])
            counts[token] = counts.get(token, 0) + 1
        rows.append("".join(chars))

    objects = [
        {
            "kind": obj["kind"],
            "type": INTERACTIVE_MAP[obj["kind"]],
            "x": obj["x"],
            "y": obj["y"],
            "note": obj.get("note", ""),
        }
        for obj in scene.get("objects", [])
        if obj.get("layer") == "objects" and obj["kind"] in INTERACTIVE_MAP
    ]

    summary = scene.get("summary", {}).get("collision_counts", {})
    return {
        "format": FORMAT,
        "name": scene["name"],
        "kind": scene["kind"],
        "seed": scene["seed"],
        "width": width,
        "height": height,
        "collision": rows,
        "legend": {"s": "solid", "w": "walk", "h": "hazard"},
        "spawn": scene["spawn"],
        "enemy_spawn": scene["enemy_spawn"],
        "interactive_objects": objects,
        "source": {
            "generator": "pixelgen",
            "version": "1.0.0",
            "profile": "techno_animist_gothic",
            "scene_export": "local pixelgen-v1.0.0-stable scene export",
            "summary": {
                "solid": summary.get("solid", counts.get("solid", 0)),
                "walk": summary.get("walk", counts.get("walk", 0)),
                "hazard": summary.get("hazard", counts.get("hazard", 0)),
            },
        },
    }


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: python tools/pixelgen_bake.py <scene.json> <output.json>")
    with open(sys.argv[1], "r", encoding="utf-8") as handle:
        scene = json.load(handle)
    baked = bake(scene)
    with open(sys.argv[2], "w", encoding="utf-8") as handle:
        json.dump(baked, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print("baked %s (%dx%d, seed %d) -> %s" % (baked["name"], baked["width"], baked["height"], baked["seed"], sys.argv[2]))


if __name__ == "__main__":
    main()
