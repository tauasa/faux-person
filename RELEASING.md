# Releasing to Maven Central

`mvn -Prelease deploy` signs the jar, the sources jar, the javadoc jar and the
POM, then uploads them to the [Central Portal](https://central.sonatype.com).
The upload stops at **validated**. Nothing is public until you press
**Publish** in the portal, and a published version can never be deleted or
replaced.

## One-time setup

1. **Central Portal account and namespace.** Sign in at
   [central.sonatype.com](https://central.sonatype.com) and add the namespace
   `org.tauasa`. The portal gives you a verification key. Add it as a DNS TXT
   record on `tauasa.org`, then press *Verify*. That one namespace covers
   `org.tauasa.apps` (this project) and `org.tauasa` (tui-java).
2. **A user token.** In the portal, open *View Account*, then *Generate User
   Token*. Put the token in `~/.m2/settings.xml`, never in this repo:

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username><!-- token username --></username>
         <password><!-- token password --></password>
       </server>
     </servers>
   </settings>
   ```

3. **A GPG key that Central can check.**

   ```bash
   gpg --full-generate-key                      # RSA 4096, your name and email
   gpg --list-secret-keys --keyid-format long   # note the key ID
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

   Central looks the public key up on a keyserver when it validates the
   upload. Signing asks for the key's passphrase when it runs.

## Each release

```bash
git switch main && git pull
# pom.xml <version> must not end in -SNAPSHOT: the release profile refuses it
mvn -Prelease clean deploy
```

Then go to *Deployments* in the portal. Check the files and versions it
lists, and press **Publish**, or **Drop** to discard the upload. After
publishing:

```bash
git tag -a v0.1.0 -m "faux-person 0.1.0" && git push origin v0.1.0
```

Then set `<version>` to the next `-SNAPSHOT`, such as `0.1.1-SNAPSHOT`.

A version can take up to 30 minutes to appear on `repo1.maven.org`, and longer
to show up in search.

## Before the first release

- **The code and data become public.** The jar, sources and javadoc are
  downloadable by anyone, even while the GitHub repo is private. The POM's
  `scm` and `url` point at `github.com/tauasa/faux-person`, so make the repo
  public too, or those links will lead nowhere.
- **The generator makes real-looking contact data.** Emails use real company
  domains and addresses use real streets, as SPEC.md's decisions record. A
  public release puts that in front of everyone who adds the dependency.
