# SentinelAgent mTLS

SentinelAgent can authenticate to the OpsSight gRPC server using mutual TLS.

The development certificate hierarchy is:

```text
NexusOps Development CA
├── OpsSight server certificate
└── SentinelAgent client certificates