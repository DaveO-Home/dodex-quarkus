@echo off

docker run -d --name envoy -p 8071:8071 -p 9901:9901 envoy:v1