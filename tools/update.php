<?php
/**
 * Endpoint de atualizacao OTA do MIDPlay (fork mrlima4095/MIDPlay).
 *
 * Fica na mesma pasta do MIDPlay.jad / MIDPlay.jar publicados e le a versao
 * direto do JAD, entao nunca precisa ser editado quando a versao muda.
 *
 * Rotas (todas GET):
 *   update.php                       -> devolve o JAD com MIDlet-Jar-URL absoluto
 *   update.php?check=1               -> JSON com a versao atual do servidor
 *   update.php?version=26.10         -> JSON com "update": true/false
 *   update.php?version=26.10&format=text -> resposta texto simples (1 linha por campo)
 *   update.php?download=1            -> redireciona pro JAR
 *
 * O celular so precisa de uma HttpConnection GET; nada de HTTPS.
 */

declare(strict_types=1);

ini_set('default_mimetype', '');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('Access-Control-Allow-Origin: *');
header('X-MIDPlay-Update: 1');

$baseDir = __DIR__;
$jadPath = $baseDir . '/MIDPlay.jad';
$jarPath = $baseDir . '/MIDPlay.jar';

if (!is_file($jadPath)) {
    http_response_code(503);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['error' => 'jad_missing'], JSON_UNESCAPED_SLASHES);
    exit;
}

/* ---------- le o JAD publicado ---------- */
$jadFields = [];
$jadLines = @file($jadPath, FILE_IGNORE_NEW_LINES);
if ($jadLines === false) {
    http_response_code(503);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['error' => 'jad_unreadable'], JSON_UNESCAPED_SLASHES);
    exit;
}
foreach ($jadLines as $line) {
    $pos = strpos($line, ':');
    if ($pos === false) {
        continue;
    }
    $name = trim(substr($line, 0, $pos));
    $value = trim(substr($line, $pos + 1));
    if ($name !== '') {
        $jadFields[$name] = $value;
    }
}

$serverVersion = isset($jadFields['MIDlet-Version']) ? $jadFields['MIDlet-Version'] : '0.0';
$jarSize = isset($jadFields['MIDlet-Jar-Size']) ? (int) $jadFields['MIDlet-Jar-Size'] : 0;
if ($jarSize <= 0 && is_file($jarPath)) {
    $jarSize = (int) filesize($jarPath);
}

/* ---------- URL absoluta (o proxy do Coolify termina o TLS) ---------- */
$https = (!empty($_SERVER['HTTPS']) && strtolower((string) $_SERVER['HTTPS']) !== 'off')
    || (isset($_SERVER['HTTP_X_FORWARDED_PROTO']) && stripos((string) $_SERVER['HTTP_X_FORWARDED_PROTO'], 'https') !== false)
    || (isset($_SERVER['SERVER_PORT']) && (int) $_SERVER['SERVER_PORT'] === 443);
$scheme = $https ? 'https' : 'http';
$host = isset($_SERVER['HTTP_HOST']) ? (string) $_SERVER['HTTP_HOST'] : 'opentty.fun';
$self = isset($_SERVER['SCRIPT_NAME']) ? (string) $_SERVER['SCRIPT_NAME'] : '/dist/debug/update.php';
$dirUrl = rtrim(str_replace('\\', '/', dirname($self)), '/');
$baseUrl = $scheme . '://' . $host . $dirUrl;

$jarUrl = $baseUrl . '/MIDPlay.jar';
$jadUrl = $baseUrl . '/MIDPlay.jad';

/* ---------- parametros ---------- */
$clientVersion = '';
if (isset($_GET['version'])) {
    $clientVersion = substr(preg_replace('/[^0-9A-Za-z._-]/', '', (string) $_GET['version']), 0, 32);
}
$format = isset($_GET['format']) ? strtolower(trim((string) $_GET['format'])) : '';
$checkMode = $clientVersion !== '' || isset($_GET['check']) || $format === 'json' || $format === 'text';

if (isset($_GET['download'])) {
    header('Location: ' . $jarUrl, true, 302);
    exit;
}

/* ---------- modo JAD ---------- */
if (!$checkMode) {
    header('Content-Type: text/vnd.sun.j2me.app-descriptor; charset=utf-8');
    header('Content-Disposition: inline; filename="MIDPlay.jad"');
    $replaced = false;
    $out = [];
    foreach ($jadLines as $line) {
        if (stripos($line, 'MIDlet-Jar-URL:') === 0) {
            $out[] = 'MIDlet-Jar-URL: ' . $jarUrl;
            $replaced = true;
        } else {
            $out[] = $line;
        }
    }
    if (!$replaced) {
        $out[] = 'MIDlet-Jar-URL: ' . $jarUrl;
    }
    echo implode("\r\n", $out) . "\r\n";
    exit;
}

$hasUpdate = $clientVersion !== ''
    ? version_compare($clientVersion, $serverVersion, '<')
    : true;

/* ---------- modo texto ---------- */
if ($format === 'text') {
    header('Content-Type: text/plain; charset=utf-8');
    echo 'version=' . $serverVersion . "\n";
    echo 'client_version=' . ($clientVersion === '' ? 'unknown' : $clientVersion) . "\n";
    echo 'update=' . ($hasUpdate ? '1' : '0') . "\n";
    echo 'jar_size=' . $jarSize . "\n";
    echo 'jar_url=' . $jarUrl . "\n";
    echo 'jad_url=' . $jadUrl . "\n";
    exit;
}

/* ---------- modo JSON (padrao) ---------- */
header('Content-Type: application/json; charset=utf-8');
$payload = [
    'app' => 'MIDPlay',
    'version' => $serverVersion,
    'client_version' => $clientVersion === '' ? null : $clientVersion,
    'update' => $hasUpdate,
    'jar_size' => $jarSize,
    'jar_url' => $jarUrl,
    'jad_url' => $jadUrl,
    'midlet' => isset($jadFields['MIDlet-1']) ? $jadFields['MIDlet-1'] : null,
];
echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT) . "\n";
