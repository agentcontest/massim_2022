#!/usr/bin/python3

"""Live broadcasts for multiagentcontest.org"""

import argparse
import asyncio
import aiohttp.web
import json
import logging
import os
import sys


MONITOR_WWW = "../monitor/src/main/resources/www"


class LiveBroadcast:
    def __init__(self, args):
        self.args = args
        self.dynamic = []
        self.step = args.step
        self.step_changed = asyncio.Condition()
        self.connected = asyncio.Condition()

        with open(os.path.join(args.path, "static.json")) as f:
            self.static = json.load(f)

        for step in range(self.static["steps"]):
            if step % 5 == 0:
                with open(os.path.join(args.path, "{}.json".format(step))) as f:
                    group = json.load(f)
            self.dynamic.append(group[str(step)])

    async def run(self):
        print("Waiting for first client ...")
        async with self.connected:
            await self.connected.wait()

        print("Waiting for delay ({}s) ...".format(args.delay))
        await asyncio.sleep(args.delay)

        print("Broadcast with {}s per frame ...".format(args.speed))
        for step in range(self.step, self.static["steps"]):
            self.step = step
            print(self.args.path, self.step, "/", self.static["steps"] - 1)

            async with self.step_changed:
                self.step_changed.notify_all()

            await asyncio.sleep(args.speed)

    async def live(self, req):
        print("Client connected.")
        async with self.connected:
            self.connected.notify()

        ws = aiohttp.web.WebSocketResponse()
        await ws.prepare(req)

        await ws.send_json(self.static)
        await ws.send_json(self.dynamic[self.step])

        while True:
            async with self.step_changed:
                await self.step_changed.wait()
                to_send = self.dynamic[self.step]

            await ws.send_json(to_send)

        return ws


def static(path):
    def handler(request):
        return aiohttp.web.FileResponse(os.path.join(os.path.dirname(__file__), path))
    return handler


async def main(args):
    broadcast = LiveBroadcast(args)

    asyncio.get_event_loop().create_task(broadcast.run())

    app = aiohttp.web.Application()
    app.router.add_route("GET", "/", static(os.path.join(MONITOR_WWW, "index.html")))
    app.router.add_route("GET", "/live/monitor", broadcast.live)
    app.router.add_static("/", MONITOR_WWW)
    return app


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", metavar="PATH", help="replay directory (containing static.json)")
    parser.add_argument("--step", type=int, default=0)
    parser.add_argument("--delay", type=float, default=10)
    parser.add_argument("--speed", type=float, default=0.5)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", default=8000)
    args = parser.parse_args()

    aiohttp.web.run_app(main(args), host=args.bind, port=args.port)
