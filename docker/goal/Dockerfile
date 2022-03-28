FROM buildpack-deps:stable-curl

ARG JAVA_VERSION

ENV JABBA_COMMAND "install ${JAVA_VERSION} -o /jdk"
RUN curl -L https://github.com/shyiko/jabba/raw/master/install.sh | bash
ENV JAVA_HOME /jdk
ENV PATH $JAVA_HOME/bin:$PATH

RUN mkdir /goal
RUN curl -sL https://bitbucket.org/goalhub/runtime/downloads/runtime-2.2.0.jar -o /goal/goal.jar
COPY goal/eismassimconfig.json /goal/