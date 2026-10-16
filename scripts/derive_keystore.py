#!/usr/bin/env python3
"""
Rebuild the app's release signing key from one secret phrase (SIGNING_SEED).

Why: Android only installs an update if it is signed with exactly the same
certificate as the installed app. This script turns the same seed into the
same RSA key and the same certificate every time, so the only thing to keep
safe is the seed (stored as a GitHub Secret, and by the owner). Nothing secret
lives in this repo.

Usage:  SIGNING_SEED=... python3 derive_keystore.py OUT.p12
Prints the keystore password on stdout (derived from the seed too).
"""
import datetime
import hashlib
import hmac
import os
import sys

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

CONTEXT = b"fbr-clients-signing-v1"
E = 65537
PRIME_BITS = 1536  # 3072-bit RSA key
ALIAS = b"fbrclients"


class Drbg:
    """Deterministic byte stream: HMAC-SHA256 in counter mode."""

    def __init__(self, seed: bytes, label: bytes):
        self.key = hmac.new(CONTEXT, seed, hashlib.sha256).digest()
        self.label = label
        self.counter = 0

    def read(self, n: int) -> bytes:
        out = b""
        while len(out) < n:
            self.counter += 1
            out += hmac.new(self.key, self.label + self.counter.to_bytes(8, "big"), hashlib.sha256).digest()
        return out[:n]

    def int_bits(self, bits: int) -> int:
        return int.from_bytes(self.read((bits + 7) // 8), "big") >> (-bits % 8)


SMALL_PRIMES = [p for p in range(3, 2000) if all(p % d for d in range(2, int(p**0.5) + 1))]


def is_probable_prime(n: int, rng: Drbg, rounds: int = 48) -> bool:
    if n < 2:
        return False
    for p in SMALL_PRIMES:
        if n % p == 0:
            return n == p
    d, s = n - 1, 0
    while d % 2 == 0:
        d //= 2
        s += 1
    for _ in range(rounds):
        a = 2 + rng.int_bits(n.bit_length() - 1) % (n - 3)
        x = pow(a, d, n)
        if x in (1, n - 1):
            continue
        for _ in range(s - 1):
            x = pow(x, 2, n)
            if x == n - 1:
                break
        else:
            return False
    return True


def make_prime(rng: Drbg, bits: int) -> int:
    while True:
        c = rng.int_bits(bits) | (0b11 << (bits - 2)) | 1  # top two bits set, odd
        if (c - 1) % E != 0 and is_probable_prime(c, rng):
            return c


def derive(seed: bytes):
    p = make_prime(Drbg(seed, b"p"), PRIME_BITS)
    q = make_prime(Drbg(seed, b"q"), PRIME_BITS)
    if p == q:
        raise SystemExit("seed produced equal primes; use another seed")
    if p < q:
        p, q = q, p
    phi = (p - 1) * (q - 1)
    d = pow(E, -1, phi)
    key = rsa.RSAPrivateNumbers(
        p=p, q=q, d=d,
        dmp1=rsa.rsa_crt_dmp1(d, p),
        dmq1=rsa.rsa_crt_dmq1(d, q),
        iqmp=rsa.rsa_crt_iqmp(p, q),
        public_numbers=rsa.RSAPublicNumbers(E, p * q),
    ).private_key()

    name = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "FBR Clients"),
        x509.NameAttribute(NameOID.COUNTRY_NAME, "PK"),
    ])
    # Every field is fixed and RSA PKCS#1 v1.5 signing is deterministic,
    # so the certificate bytes are identical on every run.
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(1)
        .not_valid_before(datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc))
        .not_valid_after(datetime.datetime(2125, 12, 31, tzinfo=datetime.timezone.utc))
        .sign(key, hashes.SHA256())
    )
    password = hashlib.sha256(CONTEXT + b"|password|" + seed).hexdigest()
    return key, cert, password


def main():
    seed = os.environ.get("SIGNING_SEED", "").strip().encode()
    if len(seed) < 24:
        raise SystemExit("SIGNING_SEED must be at least 24 characters")
    out = sys.argv[1]
    key, cert, password = derive(seed)
    data = pkcs12.serialize_key_and_certificates(
        ALIAS, key, cert, None, serialization.BestAvailableEncryption(password.encode())
    )
    with open(out, "wb") as f:
        f.write(data)
    fp = hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest()
    print(password)
    print(f"certificate SHA-256: {fp}", file=sys.stderr)


if __name__ == "__main__":
    main()
