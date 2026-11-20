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
   record on `tauasa.org`, then press *Verify*. This project and tui-java both
   publish under `org.tauasa`.
2. **A user token.** In the portal, open *View Account*, then *Generate User
   Token*. `~/.m2/settings.xml` reads it from two environment variables, so
   the token is never stored in a file, and never in this repo:

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>${env.MAVEN_CENTRAL_USERNAME}</username>
         <password>${env.MAVEN_CENTRAL_PASSWORD}</password>
       </server>
     </servers>
   </settings>
   ```

   Set both in the terminal you release from, or keep them in the macOS
   Keychain and export them from `~/.zshrc`.

3. **A GPG key that Central can check.**

   ```bash
   gpg --full-generate-key                      # RSA 4096, your name and email
   gpg --list-secret-keys --keyid-format long   # note the key ID
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

   Central looks the public key up on a keyserver when it validates the
   upload. Signing asks for the key's passphrase when it runs; on macOS, set
   `export GPG_TTY=$(tty)` first, or gpg fails with exit code 2.

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
- **The generator makes real-looking contact data.** By default employers are
  real companies marked with a `[faux]` suffix, and emails are under the reserved
  `.example` domain, so they can't deliver. `realCompanies(true)`, or the CLI's
  `--real-companies`, drops the marks and uses real company names and email
  domains. Addresses use real streets. SPEC.md's decisions record each choice.
  A public release puts that in front of everyone who adds the dependency, and
  whether real company names are acceptable, even behind a switch, is a
  question for a lawyer before the first release.
