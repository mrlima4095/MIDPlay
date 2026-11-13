package midplay.player;

/**
 * Controlador do loop A/B para o player MMAPI.
 *
 * <p>Como o seek em stream HTTP vira reload completo ({@code startSeekReload}), o loop A/B
 * funciona melhor em faixa local/baixada (setMediaTime direto, gap ~50-200ms). Em stream
 * HTTP com content-length o gap pode chegar a 1-3s.
 */
public final class AbLoopController {

  /** Span mínimo entre A e B: 500 ms. */
  public static final long MIN_LOOP_SPAN_MICROS = 500000L;

  /** Rearma quando a posição atual fica esta margem abaixo de B. */
  private static final long REARM_MARGIN_MICROS = 300000L;

  private long pointAMicros = -1L;
  private long pointBMicros = -1L;
  private boolean enabled = false;
  private boolean armed = true;

  public synchronized long getPointA() {
    return pointAMicros;
  }

  public synchronized long getPointB() {
    return pointBMicros;
  }

  public synchronized boolean isEnabled() {
    return enabled;
  }

  public synchronized boolean isReady() {
    return pointAMicros >= 0 && pointBMicros > pointAMicros + MIN_LOOP_SPAN_MICROS;
  }

  public synchronized boolean setPointA(long micros) {
    if (micros < 0L) {
      return false;
    }
    if (pointBMicros >= 0L && micros >= pointBMicros - MIN_LOOP_SPAN_MICROS) {
      return false;
    }
    pointAMicros = micros;
    armed = true;
    return true;
  }

  public synchronized boolean setPointB(long micros) {
    if (micros < 0L) {
      return false;
    }
    if (pointAMicros >= 0L && micros <= pointAMicros + MIN_LOOP_SPAN_MICROS) {
      return false;
    }
    pointBMicros = micros;
    armed = true;
    return true;
  }

  public synchronized boolean setEnabled(boolean on) {
    if (on && !isReady()) {
      return false;
    }
    enabled = on;
    armed = true;
    return true;
  }

  public synchronized void clear() {
    pointAMicros = -1L;
    pointBMicros = -1L;
    enabled = false;
    armed = true;
  }

  /**
   * Retorna o ponto A quando a posição atual cruzou B (e deve saltar), ou -1 se nada a fazer.
   * Desarma após disparar para não repetir antes de o player voltar pra dentro do intervalo.
   */
  public synchronized long consumeFireAt(long currentMicros) {
    if (!enabled || !isReady()) {
      return -1L;
    }
    if (currentMicros < pointBMicros - REARM_MARGIN_MICROS) {
      armed = true;
      return -1L;
    }
    if (!armed || currentMicros < pointBMicros) {
      return -1L;
    }
    armed = false;
    return pointAMicros;
  }
}
