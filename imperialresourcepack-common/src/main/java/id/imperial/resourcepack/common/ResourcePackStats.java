package id.imperial.resourcepack.common;

import java.util.concurrent.atomic.AtomicLong;

public final class ResourcePackStats {
  private final AtomicLong sent = new AtomicLong();
  private final AtomicLong accepted = new AtomicLong();
  private final AtomicLong loaded = new AtomicLong();
  private final AtomicLong declined = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private final AtomicLong reloads = new AtomicLong();

  public void sent() { sent.incrementAndGet(); }
  public void accepted() { accepted.incrementAndGet(); }
  public void loaded() { loaded.incrementAndGet(); }
  public void declined() { declined.incrementAndGet(); }
  public void failed() { failed.incrementAndGet(); }
  public void reload() { reloads.incrementAndGet(); }

  public long sentCount() { return sent.get(); }
  public long acceptedCount() { return accepted.get(); }
  public long loadedCount() { return loaded.get(); }
  public long declinedCount() { return declined.get(); }
  public long failedCount() { return failed.get(); }
  public long reloadCount() { return reloads.get(); }
}
