import sys
import logging
import time
import queue
import threading
import readline

import websocket

server_addr = input("Please enter Spigot/Minecraft server address: ")
ws_addr = "ws://%s:4721" % server_addr

taskQ = queue.Queue()
replyQ = queue.Queue()


def on_ping(ws, message):
    ws.send("pong")


def on_message(ws, message):
    # print("got from server: ", message)
    replyQ.put(message)

ws = websocket.WebSocketApp(ws_addr,
                            on_ping=on_ping,
                            on_message=on_message)



def taskWorker():
    while True:
        task = taskQ.get()
        ws.send(task)
        taskQ.task_done()

task_worker_thread = threading.Thread(target=taskWorker,
                                      daemon=True)
task_worker_thread.start()

try:
    ws_thread = threading.Thread(target=ws.run_forever,
                                 daemon=True)
    ws_thread.start()
except Exception as err:
    exit(err)

try:
    while True:
        command = input("command: ")
        if command:
            if command == "exit":
                exit("Bye!")
            else:
                taskQ.put(command)
        time.sleep(0.5)
        while not replyQ.empty():
            print(replyQ.get())
except KeyboardInterrupt:
    exit()
