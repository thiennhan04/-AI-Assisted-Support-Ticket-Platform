$ErrorActionPreference = "Stop"

$keyDirectory = Join-Path $PSScriptRoot "..\.local\keys"
$privateKeyPath = Join-Path $keyDirectory "identity-private.pem"
$publicKeyPath = Join-Path $keyDirectory "identity-public.pem"
$generatorPath = Join-Path $keyDirectory "GenerateIdentityKeys.java"

New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null

$generatorSource = @'
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

class GenerateIdentityKeys {
    public static void main(String[] args) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        writePem(Path.of(args[0]), "PRIVATE KEY", pair.getPrivate().getEncoded());
        writePem(Path.of(args[1]), "PUBLIC KEY", pair.getPublic().getEncoded());
    }

    private static void writePem(Path path, String type, byte[] encoded) throws Exception {
        var body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body
                + "\n-----END " + type + "-----\n");
    }
}
'@

try {
    [System.IO.File]::WriteAllText($generatorPath, $generatorSource)
    & java $generatorPath $privateKeyPath $publicKeyPath
    if ($LASTEXITCODE -ne 0) {
        throw "Java failed to generate the Identity RSA key pair"
    }
} finally {
    if (Test-Path -LiteralPath $generatorPath) {
        Remove-Item -LiteralPath $generatorPath -Force
    }
}

Write-Output "Generated local Identity RSA keys in $keyDirectory"
