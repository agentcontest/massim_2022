FROM maven:3.8.4-openjdk-17

WORKDIR /usr/javaagents

RUN git clone https://github.com/agentcontest/massim_2020.git .
RUN mvn clean package

COPY lib/eismassimconfig.json ./javaagents/conf/BasicAgents