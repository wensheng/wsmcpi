# WSMCPI Client Example in Python
This is basically a Python websocket client talking to WSMCPI plugin of Spigot Minecraft server.

## Setup
It only requires [websocket-client](https://pypi.org/project/websocket-client/).

Setup a Python virtualenv, install websocket-client and execute client.py:

    python3 -m venv venv
    ./venv/bin/pip install websocket-client
    ./venv/bin/python client.py

Enter the Spigot server address such as 'localhost', then you can send API commands to Spigot.
