# keycloak-login-recaptcha

Keycloak module to add recaptcha into the login template.

# Configuration
* Run `mvn clean install`. it will produce `target/recaptcha-login.jar`.
* Then start keycloak with `docker-compose up`
    * the `target/recaptcha-login.jar` is mounted to `/opt/jboss/keycloak/standalone/deployments/recaptcha-login.jar`, so it will
      be detected by JBoss automatically as a plugin.
    * `login.ftl` is the login template inside `base` theme for keycloak in `/opt/jboss/keycloak/themes/base/login/login.ftl`.
      We decided to override it to add recaptcha related items into it directly. 

Inside the `login.ftl`, We have just added this part inside `<form></form>` section.
So inside `RecaptchaUsernamePasswordForm` we will set `recaptchaRequired` to `true`, and this part
of `login.ftl` will be enabled for recaptcha based logins.
If using normal `UsernamePasswordForm` then this part will not be enabled in the login form.
```
<#if recaptchaRequired??>
    <script>
        function onSubmit(token) {
            document.getElementById("kc-form-login").submit();
        }
    </script>
    <script src="https://www.google.com/recaptcha/api.js" async defer></script>
    <button tabindex="4" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!} g-recaptcha" name="login" id="kc-login" data-sitekey="${recaptchaSiteKey}" data-callback='onSubmit' data-action='submit'>${msg("doLogIn")}</button>
<#else>
    <input tabindex="4" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
</#if>
```

### Realm
A realm is going to be created from the realm.json file with the following configuration:

* Realm -> Security Defenses
  * X-Frame-Options -> `ALLOW-FROM https://www.google.com`
  * Content-Security-Policy -> frame-src 'self' https://www.google.com; frame-ancestors 'self'; object-src 'none';`

* Authentication
  * A new Authentication Flow was created
  * The`Recaptcha Username Password Form` execution was creted
  * The Recaptcha Site Key and Recaptcha Secret were added in the execution configuration
  * The recaptcha flow created was bound to the Browser Flow

## Troubleshooting
```sh
/opt/jboss/keycloak/bin/jboss-cli.sh --connect

/subsystem=logging/logger=com.identicum.keycloak_recaptcha:add
/subsystem=logging/logger=com.identicum.keycloak_recaptcha:write-attribute(name="level", value=DEBUG)
```