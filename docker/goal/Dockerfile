FROM openjdk:17-oracle

ARG GOAL_URL

RUN mkdir /goal
RUN curl -sL ${GOAL_URL} -o /goal/goal.jar
COPY goal/eismassimconfig.json /goal/