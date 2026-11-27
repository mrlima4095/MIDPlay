# TinyTLS
Minimal TLS implementation for J2ME.

This implementation is not secure or robust. It is only designed to be the
bare minimum to connect to services that require TLS 1.2.

It has been designed around the following requirements:
- Support for CLDC 1.0
- TLS 1.2 only
- TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256 cipher suite only
- No certificate checking
- No secure random numbers

## Usage
Intended usage is via the **modified version of [ModernConnector](https://github.com/ProgrammerIn-wonderland/ModernConnector)** included in the Discofi repo.

You may also use:

```java
TinyTLS tls = new TinyTLS(null);
tls.connect("example.com", 443);
InputStream is = tls.openInputStream();
OutputStream os = tls.openOutputStream();
```

Note that most J2ME devices block socket connections to port 443 if the app is unsigned.
Due to this, TinyTLS (and any other TLS stack for J2ME phones) will only work in the following cases:

- the server accepts connections from a port other than 80, 443, or 8080 - for example 8443
- your device has a way to install Java code-signing certificates (Nokia, Sony Ericsson)
- your device has a way to remove this restriction
- your device does not have this restriction (usually emulators)
- your device is not JTWI-compliant (mainly older devices, e.g. Motorola)

## Logging

Debug logging can be enabled by supplying a StringBuffer to the constructor.

Note that if you are using [gtrxAC/j2me-template](https://github.com/gtrxAC/j2me-template) or a build system with
compatible pre-processing, you will need to enable the `DEBUG` define to use logging.

```java
StringBuffer log = new StringBuffer();
TinyTLS tls = new TinyTLS(log);
// ...
displayLog(log.toString());
```