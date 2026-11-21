"""Master the two language versions from the same reviewed in-game take and revised cues."""
from pathlib import Path
import subprocess
import sys
base=Path(__file__).resolve().parent
for number in range(1,6):
    subprocess.run([sys.executable,str(base/'finishing.py'),f'short{number}',str(base/'takes')],check=True)
