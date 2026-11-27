"""Shared project reader for the editor and score."""
from pathlib import Path
import json
import re


def project(path):
    doc = json.loads(Path(path).read_text(encoding="utf-8"))
    assert isinstance(doc["brand"], str) and doc["brand"].strip()
    assert doc["films"], "Project needs a film"
    for key, film in doc["films"].items():
        assert re.fullmatch(r"[a-z0-9_]+", key)
        assert re.fullmatch(r"[A-Za-z0-9_-]+", film["name"])
        assert film["music"] in ("pillar", "tiptoe", "crew", "brew", "farm")
        assert film["shots"], "Film needs a shot"
        for shot in film["shots"]:
            assert re.fullmatch(r"[A-Za-z0-9_-]+", shot["source"])
            assert shot["duration"] >= 0.7
            assert shot.get("seek", 0) >= 0 and shot.get("length", 1) > 0
            assert shot.get("effect") in (None, "stage", "white-reveal", "portrait")
            if shot.get("effect") == "portrait":
                assert shot["duration"] > 3.3
            for cue in shot.get("dialogue", []):
                assert 0 <= cue["start"] < cue["end"] <= shot["duration"]
                assert 0 <= cue["x"] <= 1 and .12 <= cue["y"] <= .85
                assert all(cue["text"][lang] for lang in ("ru", "en"))
        for at, name, gain, semitones in film.get("effects", []):
            assert at >= 0 and gain >= 0 and -36 <= semitones <= 36
            assert re.fullmatch(r"[a-z0-9_-]+", name), "Invalid sound name"
    return doc

