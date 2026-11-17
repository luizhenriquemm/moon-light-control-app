Test-only TLS identities for the self-signed HTTPS tests (`ConsoleScreenTest`, `DownloaderTest`).
Two different self-signed certificates for 127.0.0.1 ("a" and "b"), password `testpass`, legacy
PKCS12 so Android can read them. Generated with:

    keytool -J-Dkeystore.pkcs12.legacy -genkeypair -alias server -keyalg RSA -keysize 2048 \
      -validity 36500 -dname "CN=127.0.0.1, O=MoonLightControl tests (a)" -ext "SAN=ip:127.0.0.1" \
      -storetype PKCS12 -keystore test-server-a.p12 -storepass testpass -keypass testpass

Never used outside instrumented tests; not secrets.
