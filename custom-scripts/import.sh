#!/bin/bash

KCADM=$JBOSS_HOME/bin/kcadm.sh
REALM_NAME=demorealm
FLOW_ALIAS=recaptcha
EXECUTION_PROVIDER_ID=recaptcha-u-p-form
USER_NAME=demo
USER_PASS=demo

for i in {1..10}; do
    $KCADM config credentials --server http://localhost:8080/auth --realm master --user $KEYCLOAK_USER --password $KEYCLOAK_PASSWORD
    custom_realm=$($KCADM get realms/demorealm)
    if [ -z "$custom_realm" ]; then
        $KCADM create realms -s realm="${REALM_NAME}" -s enabled=true -s registrationAllowed=true

        $KCADM create authentication/flows -r $REALM_NAME -s alias=$FLOW_ALIAS -s providerId=basic-flow -s topLevel=true -s builtIn=false
        EXECUTION_ID=$($KCADM create authentication/flows/"$FLOW_ALIAS"/executions/execution -i -b '{"provider" : "'"$EXECUTION_PROVIDER_ID"'"}' -r "$REALM_NAME")
        $KCADM update realms/${REALM_NAME} -s browserFlow=$FLOW_ALIAS

        $KCADM update realms/${REALM_NAME} -f /opt/jboss/keycloak/objects/security-defenses.json

        $KCADM create users -r $REALM_NAME -s username=$USER_NAME -s enabled=true
        $KCADM set-password -r $REALM_NAME --username $USER_NAME --new-password $USER_PASS
    else
        echo "The custom realm already exists."
        exit
    fi
    sleep 5s
done
