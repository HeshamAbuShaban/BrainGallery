# Development signing key

`braingallery-dev.jks` is a **development-only** signing key. It is committed so
that every CI build produces APKs with the same signature and can be installed
and upgraded in place (GitHub Actions otherwise generates a throwaway debug
keystore per run, which makes each build uninstallable).

- alias / store password / key password: `braingallery`
- valid for 10000 days

**Before distributing this app to anyone**, replace it with your own release key
and keep that key out of version control (store it as a GitHub Actions secret and
add a `keystore.properties` file at build time). Right now this key is in a public
repository, so anyone can sign an update that claims to be BrainGallery — fine for
personal sideloading, not fine for a published app.
