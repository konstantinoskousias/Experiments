#/bin/sh

REPO=monroe1.cs.kau.se:5000
CONTAINER=monroe_base_internal
DOCKERFILE=${CONTAINER}.docker
CONTAINERTAG=${REPO}/monroe/${CONTAINER}

docker login ${REPO} && docker build --rm=true -f ${DOCKERFILE} -t ${CONTAINER} . && docker tag ${CONTAINER} ${CONTAINERTAG} && docker push ${CONTAINERTAG} && echo "Now rebuild all images that are built on ${CONTAINERTAG}"


