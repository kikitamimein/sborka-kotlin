import shutil
import os
import subprocess
import sys

print(f"Python executable: {sys.executable}")
print(f"Current working directory: {os.getcwd()}")
print("-" * 20)
print("Checking for bash...")
bash_path = shutil.which("bash")
print(f"shutil.which('bash'): {bash_path}")

print("-" * 20)
print("PATH environment variable:")
for p in os.environ.get("PATH", "").split(os.pathsep):
    print(f"  {p}")

print("-" * 20)
print("Attempting to run 'bash --version' directly:")
try:
    subprocess.run(["bash", "--version"], check=True)
    print("Direct run: SUCCESS")
except Exception as e:
    print(f"Direct run: FAILED ({e})")

print("-" * 20)
print("Attempting to run 'env bash --version':")
try:
    subprocess.run(["env", "bash", "--version"], check=True)
    print("Env run: SUCCESS")
except Exception as e:
    print(f"Env run: FAILED ({e})")

print("-" * 20)
print("Checking /bin/bash and /usr/bin/bash:")
print(f"/bin/bash exists: {os.path.exists('/bin/bash')}")
print(f"/usr/bin/bash exists: {os.path.exists('/usr/bin/bash')}")
