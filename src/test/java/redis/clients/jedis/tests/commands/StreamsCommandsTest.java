package redis.clients.jedis.tests.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static redis.clients.jedis.StreamGroupInfo.*;
import static redis.clients.jedis.StreamInfo.*;
import static redis.clients.jedis.StreamConsumersInfo.IDLE;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import redis.clients.jedis.*;
import redis.clients.jedis.Protocol.Keyword;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XAutoClaimParams;
import redis.clients.jedis.params.XClaimParams;
import redis.clients.jedis.params.XPendingParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.params.XReadParams;
import redis.clients.jedis.params.XTrimParams;
import redis.clients.jedis.util.SafeEncoder;

public class StreamsCommandsTest extends JedisCommandTestBase {

  @Test
  public void xadd() {

    try {
      Map<String, String> map1 = new HashMap<>();
      jedis.xadd("stream1", null, map1);
      fail();
    } catch (JedisDataException expected) {
      assertEquals("ERR wrong number of arguments for 'xadd' command", expected.getMessage());
    }

    Map<String, String> map1 = new HashMap<>();
    map1.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xadd-stream1", null, map1);
    assertNotNull(id1);

    Map<String, String> map2 = new HashMap<>();
    map2.put("f1", "v1");
    map2.put("f2", "v2");
    StreamEntryID id2 = jedis.xadd("xadd-stream1", null, map2);
    assertTrue(id2.compareTo(id1) > 0);

    Map<String, String> map3 = new HashMap<>();
    map3.put("f2", "v2");
    map3.put("f3", "v3");
    StreamEntryID id3 = jedis.xadd("xadd-stream2", null, map3);

    Map<String, String> map4 = new HashMap<>();
    map4.put("f2", "v2");
    map4.put("f3", "v3");
    StreamEntryID idIn = new StreamEntryID(id3.getTime() + 1, 1L);
    StreamEntryID id4 = jedis.xadd("xadd-stream2", idIn, map4);
    assertEquals(idIn, id4);
    assertTrue(id4.compareTo(id3) > 0);

    Map<String, String> map5 = new HashMap<>();
    map5.put("f4", "v4");
    map5.put("f5", "v5");
    StreamEntryID id5 = jedis.xadd("xadd-stream2", null, map5);
    assertTrue(id5.compareTo(id4) > 0);

    Map<String, String> map6 = new HashMap<>();
    map6.put("f4", "v4");
    map6.put("f5", "v5");
    StreamEntryID id6 = jedis.xadd("xadd-stream2", null, map6, 3, false);
    assertTrue(id6.compareTo(id5) > 0);
    assertEquals(3L, jedis.xlen("xadd-stream2").longValue());
  }

  @Test
  public void xaddWithParams() {

    try {
      jedis.xadd("stream1", new HashMap<>(), XAddParams.xAddParams());
      fail();
    } catch (JedisDataException expected) {
      assertEquals("ERR wrong number of arguments for 'xadd' command", expected.getMessage());
    }

    StreamEntryID id1 = jedis.xadd("xadd-stream1", null, Collections.singletonMap("f1", "v1"));
    assertNotNull(id1);

    Map<String, String> map2 = new HashMap<>();
    map2.put("f1", "v1");
    map2.put("f2", "v2");
    StreamEntryID id2 = jedis.xadd("xadd-stream1", map2, XAddParams.xAddParams());
    assertTrue(id2.compareTo(id1) > 0);

    Map<String, String> map3 = new HashMap<>();
    map3.put("f2", "v2");
    map3.put("f3", "v3");
    StreamEntryID id3 = jedis.xadd("xadd-stream2", map3, XAddParams.xAddParams());

    Map<String, String> map4 = new HashMap<>();
    map4.put("f2", "v2");
    map4.put("f3", "v3");
    StreamEntryID idIn = new StreamEntryID(id3.getTime() + 1, 1L);
    StreamEntryID id4 = jedis.xadd("xadd-stream2", map4, XAddParams.xAddParams().id(idIn.toString()));
    assertEquals(idIn, id4);
    assertTrue(id4.compareTo(id3) > 0);

    Map<String, String> map5 = new HashMap<>();
    map5.put("f4", "v4");
    map5.put("f5", "v5");
    StreamEntryID id5 = jedis.xadd("xadd-stream2", map5, XAddParams.xAddParams());
    assertTrue(id5.compareTo(id4) > 0);

    Map<String, String> map6 = new HashMap<>();
    map6.put("f4", "v4");
    map6.put("f5", "v5");
    StreamEntryID id6 = jedis.xadd("xadd-stream2", map6,
      XAddParams.xAddParams().maxLen(3).exactTrimming());
    assertTrue(id6.compareTo(id5) > 0);
    assertEquals(3L, jedis.xlen("xadd-stream2").longValue());

    // nomkstream
    StreamEntryID id7 = jedis.xadd("xadd-stream3", map6,
      XAddParams.xAddParams().noMkStream().maxLen(3).exactTrimming());
    assertNull(id7);
    assertFalse(jedis.exists("xadd-stream3"));

    // minid
    jedis.xadd("xadd-stream3", map6, XAddParams.xAddParams().minId("2").id("2"));
    assertEquals(1, jedis.xlen("xadd-stream3").longValue());
    jedis.xadd("xadd-stream3", map6, XAddParams.xAddParams().minId("4").id("3"));
    assertEquals(0, jedis.xlen("xadd-stream3").longValue());
  }

  @Test
  public void xdel() {
    Map<String, String> map1 = new HashMap<>();
    map1.put("f1", "v1");

    StreamEntryID id1 = jedis.xadd("xdel-stream", null, map1);
    assertNotNull(id1);

    StreamEntryID id2 = jedis.xadd("xdel-stream", null, map1);
    assertNotNull(id2);
    assertEquals(2L, jedis.xlen("xdel-stream").longValue());

    assertEquals(1L, jedis.xdel("xdel-stream", id1));
    assertEquals(1L, jedis.xlen("xdel-stream").longValue());
  }

  @Test
  public void xlen() {
    assertEquals(0L, jedis.xlen("xlen-stream").longValue());

    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    jedis.xadd("xlen-stream", null, map);
    assertEquals(1L, jedis.xlen("xlen-stream").longValue());

    jedis.xadd("xlen-stream", null, map);
    assertEquals(2L, jedis.xlen("xlen-stream").longValue());
  }

  @Test
  public void xrange() {
    List<StreamEntry> range = jedis.xrange("xrange-stream", (StreamEntryID) null,
      (StreamEntryID) null, Integer.MAX_VALUE);
    assertEquals(0, range.size());

    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xrange-stream", null, map);
    StreamEntryID id2 = jedis.xadd("xrange-stream", null, map);
    List<StreamEntry> range2 = jedis.xrange("xrange-stream", (StreamEntryID) null,
      (StreamEntryID) null, 3);
    assertEquals(2, range2.size());

    List<StreamEntry> range3 = jedis.xrange("xrange-stream", id1, null, 2);
    assertEquals(2, range3.size());

    List<StreamEntry> range4 = jedis.xrange("xrange-stream", id1, id2, 2);
    assertEquals(2, range4.size());

    List<StreamEntry> range5 = jedis.xrange("xrange-stream", id1, id2, 1);
    assertEquals(1, range5.size());

    List<StreamEntry> range6 = jedis.xrange("xrange-stream", id2, null, 4);
    assertEquals(1, range6.size());

    StreamEntryID id3 = jedis.xadd("xrange-stream", null, map);
    List<StreamEntry> range7 = jedis.xrange("xrange-stream", id3, id3, 4);
    assertEquals(1, range7.size());

    List<StreamEntry> range8 = jedis.xrange("xrange-stream", null, null);
    assertEquals(3, range8.size());

    // count parameter - backward compatibility
    List<byte[]> cRange = jedis.xrange("xrange-stream".getBytes(), id1.toString().getBytes(),
        id2.toString().getBytes(), 10L + Integer.MAX_VALUE);
    assertEquals(2, cRange.size());
  }

  @Test
  public void xread() {

    Entry<String, StreamEntryID> streamQeury1 = new AbstractMap.SimpleImmutableEntry<>(
        "xread-stream1", new StreamEntryID());

    // Empty Stream
    List<Entry<String, List<StreamEntry>>> range = jedis.xread(1, 1L, streamQeury1);
    assertEquals(0, range.size());

    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xread-stream1", null, map);
    StreamEntryID id2 = jedis.xadd("xread-stream2", null, map);

    // Read only a single Stream
    List<Entry<String, List<StreamEntry>>> streams1 = jedis.xread(1, 1L, streamQeury1);
    assertEquals(1, streams1.size());

    // Read from two Streams
    Entry<String, StreamEntryID> streamQuery2 = new AbstractMap.SimpleImmutableEntry<>(
        "xread-stream1", new StreamEntryID());
    Entry<String, StreamEntryID> streamQuery3 = new AbstractMap.SimpleImmutableEntry<>(
        "xread-stream2", new StreamEntryID());
    List<Entry<String, List<StreamEntry>>> streams2 = jedis
        .xread(2, 1L, streamQuery2, streamQuery3);
    assertEquals(2, streams2.size());

  }

  @Test
  public void xreadWithParams() {

    Map<String, StreamEntryID> streamQeury1 = Collections.singletonMap("xread-stream1", new StreamEntryID());

    // Before creating Stream
    assertNull(jedis.xread(XReadParams.xReadParams().block(1), streamQeury1));
    assertNull(jedis.xread(XReadParams.xReadParams(), streamQeury1));

    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xread-stream1", null, map);
    StreamEntryID id2 = jedis.xadd("xread-stream2", null, map);

    // Read only a single Stream
    List<Entry<String, List<StreamEntry>>> streams1 = jedis.xread(XReadParams.xReadParams().count(1).block(1), streamQeury1);
    assertEquals(1, streams1.size());
    assertEquals("xread-stream1", streams1.get(0).getKey());
    assertEquals(1, streams1.get(0).getValue().size());
    assertEquals(id1, streams1.get(0).getValue().get(0).getID());
    assertEquals(map, streams1.get(0).getValue().get(0).getFields());

    assertNull(jedis.xread(XReadParams.xReadParams().block(1), Collections.singletonMap("xread-stream1", id1)));
    assertNull(jedis.xread(XReadParams.xReadParams(), Collections.singletonMap("xread-stream1", id1)));

    // Read from two Streams
    Map<String, StreamEntryID> streamQuery23 = new LinkedHashMap<>();
    streamQuery23.put("xread-stream1", new StreamEntryID());
    streamQuery23.put("xread-stream2", new StreamEntryID());
    List<Entry<String, List<StreamEntry>>> streams2 = jedis.xread(XReadParams.xReadParams().count(2).block(1), streamQuery23);
    assertEquals(2, streams2.size());
  }

  @Test
  public void xreadBlockZero() throws InterruptedException {
    final AtomicReference<StreamEntryID> readId = new AtomicReference<>();
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try (Jedis blockJedis = createJedis()) {
          long startTime = System.currentTimeMillis();
          List<Entry<String, List<StreamEntry>>> read = blockJedis.xread(XReadParams.xReadParams().block(0),
              Collections.singletonMap("block0-stream", new StreamEntryID()));
          long endTime = System.currentTimeMillis();
          assertTrue(endTime - startTime > 500);
          assertNotNull(read);
          readId.set(read.get(0).getValue().get(0).getID());
        }
      }
    }, "xread-block-0-thread");
    t.start();
    Thread.sleep(1000);
    StreamEntryID addedId = jedis.xadd("block0-stream", null, Collections.singletonMap("foo", "bar"));
    t.join();
    assertEquals(addedId, readId.get());
  }

  @Test
  public void xtrim() {
    Map<String, String> map1 = new HashMap<String, String>();
    map1.put("f1", "v1");

    for (int i = 1; i <= 5; i++) {
      jedis.xadd("xtrim-stream", null, map1);
    }
    assertEquals(5L, jedis.xlen("xtrim-stream").longValue());

    jedis.xtrim("xtrim-stream", 3, false);
    assertEquals(3L, jedis.xlen("xtrim-stream").longValue());
  }

  @Test
  public void xtrimWithParams() {
    Map<String, String> map1 = new HashMap<>();
    map1.put("f1", "v1");
    for (int i = 1; i <= 5; i++) {
      jedis.xadd("xtrim-stream", new StreamEntryID("0-" + i), map1);
    }
    assertEquals(5L, jedis.xlen("xtrim-stream").longValue());

    jedis.xtrim("xtrim-stream", XTrimParams.xTrimParams().maxLen(3).exactTrimming());
    assertEquals(3L, jedis.xlen("xtrim-stream").longValue());

    // minId
    jedis.xtrim("xtrim-stream", XTrimParams.xTrimParams().minId("0-4").exactTrimming());
    assertEquals(2L, jedis.xlen("xtrim-stream").longValue());
  }

  @Test
  public void xrevrange() {
    List<StreamEntry> range = jedis.xrevrange("xrevrange-stream", (StreamEntryID) null,
      (StreamEntryID) null, Integer.MAX_VALUE);
    assertEquals(0, range.size());

    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xrevrange-stream", null, map);
    StreamEntryID id2 = jedis.xadd("xrevrange-stream", null, map);
    List<StreamEntry> range2 = jedis.xrange("xrevrange-stream", (StreamEntryID) null,
      (StreamEntryID) null, 3);
    assertEquals(2, range2.size());

    List<StreamEntry> range3 = jedis.xrevrange("xrevrange-stream", null, id1, 2);
    assertEquals(2, range3.size());

    List<StreamEntry> range4 = jedis.xrevrange("xrevrange-stream", id2, id1, 2);
    assertEquals(2, range4.size());

    List<StreamEntry> range5 = jedis.xrevrange("xrevrange-stream", id2, id1, 1);
    assertEquals(1, range5.size());

    List<StreamEntry> range6 = jedis.xrevrange("xrevrange-stream", null, id2, 4);
    assertEquals(1, range6.size());

    StreamEntryID id3 = jedis.xadd("xrevrange-stream", null, map);
    List<StreamEntry> range7 = jedis.xrevrange("xrevrange-stream", id3, id3, 4);
    assertEquals(1, range7.size());

    List<StreamEntry> range8 = jedis.xrevrange("xrevrange-stream", null, null);
    assertEquals(3, range8.size());
  }

  @Test
  public void xgroup() {

    Map<String, String> map = new HashMap<String, String>();
    map.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xgroup-stream", null, map);

    String status = jedis.xgroupCreate("xgroup-stream", "consumer-group-name", null, false);
    assertTrue(Keyword.OK.name().equalsIgnoreCase(status));

    status = jedis.xgroupSetID("xgroup-stream", "consumer-group-name", id1);
    assertTrue(Keyword.OK.name().equalsIgnoreCase(status));

    status = jedis.xgroupCreate("xgroup-stream", "consumer-group-name1", StreamEntryID.LAST_ENTRY,
      false);
    assertTrue(Keyword.OK.name().equalsIgnoreCase(status));

    jedis.xgroupDestroy("xgroup-stream", "consumer-group-name");

    Long pendingMessageNum = jedis.xgroupDelConsumer("xgroup-stream", "consumer-group-name1",
      "myconsumer1");
    assertEquals(0L, pendingMessageNum.longValue());
  }

  @Test
  public void xreadGroup() {

    // Simple xreadGroup with NOACK
    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xreadGroup-stream1", null, map);
    String status1 = jedis.xgroupCreate("xreadGroup-stream1", "xreadGroup-group", null, false);
    Entry<String, StreamEntryID> streamQeury1 = new AbstractMap.SimpleImmutableEntry<>(
        "xreadGroup-stream1", StreamEntryID.UNRECEIVED_ENTRY);
    List<Entry<String, List<StreamEntry>>> range = jedis.xreadGroup("xreadGroup-group",
      "xreadGroup-consumer", 1, 0, true, streamQeury1);
    assertEquals(1, range.size());
    assertEquals(1, range.get(0).getValue().size());

    StreamEntryID id2 = jedis.xadd("xreadGroup-stream1", null, map);
    StreamEntryID id3 = jedis.xadd("xreadGroup-stream2", null, map);
    String status2 = jedis.xgroupCreate("xreadGroup-stream2", "xreadGroup-group", null, false);

    // Read only a single Stream
    Entry<String, StreamEntryID> streamQeury11 = new AbstractMap.SimpleImmutableEntry<>(
        "xreadGroup-stream1", StreamEntryID.UNRECEIVED_ENTRY);
    List<Entry<String, List<StreamEntry>>> streams1 = jedis.xreadGroup("xreadGroup-group",
      "xreadGroup-consumer", 1, 1L, true, streamQeury11);
    assertEquals(1, streams1.size());
    assertEquals(1, streams1.get(0).getValue().size());

    // Read from two Streams
    Entry<String, StreamEntryID> streamQuery2 = new AbstractMap.SimpleImmutableEntry<String, StreamEntryID>(
        "xreadGroup-stream1", new StreamEntryID());
    Entry<String, StreamEntryID> streamQuery3 = new AbstractMap.SimpleImmutableEntry<String, StreamEntryID>(
        "xreadGroup-stream2", new StreamEntryID());
    List<Entry<String, List<StreamEntry>>> streams2 = jedis.xreadGroup("xreadGroup-group",
      "xreadGroup-consumer", 1, 1L, true, streamQuery2, streamQuery3);
    assertEquals(2, streams2.size());

    // Read only fresh messages
    StreamEntryID id4 = jedis.xadd("xreadGroup-stream1", null, map);
    Entry<String, StreamEntryID> streamQeuryFresh = new AbstractMap.SimpleImmutableEntry<String, StreamEntryID>(
        "xreadGroup-stream1", StreamEntryID.UNRECEIVED_ENTRY);
    List<Entry<String, List<StreamEntry>>> streams3 = jedis.xreadGroup("xreadGroup-group",
      "xreadGroup-consumer", 4, 100L, true, streamQeuryFresh);
    assertEquals(1, streams3.size());
    assertEquals(id4, streams3.get(0).getValue().get(0).getID());
  }

  @Test
  public void xreadGroupWithParams() {

    // Simple xreadGroup with NOACK
    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xreadGroup-stream1", null, map);
    jedis.xgroupCreate("xreadGroup-stream1", "xreadGroup-group", null, false);
    Map<String, StreamEntryID> streamQeury1 = Collections.singletonMap("xreadGroup-stream1", StreamEntryID.UNRECEIVED_ENTRY);
    List<Entry<String, List<StreamEntry>>> range = jedis.xreadGroup("xreadGroup-group", "xreadGroup-consumer",
        XReadGroupParams.xReadGroupParams().count(1).noAck(), streamQeury1);
    assertEquals(1, range.size());
    assertEquals(1, range.get(0).getValue().size());

    StreamEntryID id2 = jedis.xadd("xreadGroup-stream1", null, map);
    StreamEntryID id3 = jedis.xadd("xreadGroup-stream2", null, map);
    jedis.xgroupCreate("xreadGroup-stream2", "xreadGroup-group", null, false);

    // Read only a single Stream
    Map<String, StreamEntryID> streamQeury11 = Collections.singletonMap("xreadGroup-stream1", StreamEntryID.UNRECEIVED_ENTRY);
    List<Entry<String, List<StreamEntry>>> streams1 = jedis.xreadGroup("xreadGroup-group", "xreadGroup-consumer",
        XReadGroupParams.xReadGroupParams().count(1).block(1).noAck(), streamQeury11);
    assertEquals(1, streams1.size());
    assertEquals(1, streams1.get(0).getValue().size());

    // Read from two Streams
    Map<String, StreamEntryID> streamQuery23 = new LinkedHashMap<>();
    streamQuery23.put("xreadGroup-stream1", new StreamEntryID());
    streamQuery23.put("xreadGroup-stream2", new StreamEntryID());
    List<Entry<String, List<StreamEntry>>> streams2 = jedis.xreadGroup("xreadGroup-group", "xreadGroup-consumer",
        XReadGroupParams.xReadGroupParams().count(1).block(1).noAck(), streamQuery23);
    assertEquals(2, streams2.size());

    // Read only fresh messages
    StreamEntryID id4 = jedis.xadd("xreadGroup-stream1", null, map);
    Map<String, StreamEntryID> streamQeuryFresh = Collections.singletonMap("xreadGroup-stream1", StreamEntryID.UNRECEIVED_ENTRY);
    List<Entry<String, List<StreamEntry>>> streams3 = jedis.xreadGroup("xreadGroup-group", "xreadGroup-consumer",
        XReadGroupParams.xReadGroupParams().count(4).block(100).noAck(), streamQeuryFresh);
    assertEquals(1, streams3.size());
    assertEquals(id4, streams3.get(0).getValue().get(0).getID());
  }

  @Test
  public void xack() {

    Map<String, String> map = new HashMap<String, String>();
    map.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xack-stream", null, map);

    String status = jedis.xgroupCreate("xack-stream", "xack-group", null, false);

    Entry<String, StreamEntryID> streamQeury1 = new AbstractMap.SimpleImmutableEntry<>(
        "xack-stream", StreamEntryID.UNRECEIVED_ENTRY);

    // Empty Stream
    List<Entry<String, List<StreamEntry>>> range = jedis.xreadGroup("xack-group", "xack-consumer",
      1, 1L, false, streamQeury1);
    assertEquals(1, range.size());

    assertEquals(1L,
      jedis.xack("xack-stream", "xack-group", range.get(0).getValue().get(0).getID()));
  }

  @Test
  public void xpending() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xpendeing-stream", null, map);

    assertEquals("OK", jedis.xgroupCreate("xpendeing-stream", "xpendeing-group", null, false));

    Entry<String, StreamEntryID> streamQeury1 = new AbstractMap.SimpleImmutableEntry<>(
        "xpendeing-stream", StreamEntryID.UNRECEIVED_ENTRY);

    // Read the event from Stream put it on pending
    List<Entry<String, List<StreamEntry>>> range = jedis.xreadGroup("xpendeing-group",
      "xpendeing-consumer", 1, 1L, false, streamQeury1);
    assertEquals(1, range.size());
    assertEquals(1, range.get(0).getValue().size());
    assertEquals(map, range.get(0).getValue().get(0).getFields());

    // Get the summary about the pending messages
    StreamPendingSummary pendingSummary = jedis.xpending("xpendeing-stream", "xpendeing-group");
    assertEquals(1, pendingSummary.getTotal());
    assertEquals(id1, pendingSummary.getMinId());
    assertEquals(1l, pendingSummary.getConsumerMessageCount().get("xpendeing-consumer").longValue());

    // Get the pending event
    List<StreamPendingEntry> pendingRange = jedis.xpending("xpendeing-stream", "xpendeing-group",
      null, null, 3, "xpendeing-consumer");
    assertEquals(1, pendingRange.size());
    assertEquals(id1, pendingRange.get(0).getID());
    assertEquals(1, pendingRange.get(0).getDeliveredTimes());
    assertEquals("xpendeing-consumer", pendingRange.get(0).getConsumerName());

    // Without consumer
    pendingRange = jedis.xpending("xpendeing-stream", "xpendeing-group", null, null, 3, null);
    assertEquals(1, pendingRange.size());
    assertEquals(id1, pendingRange.get(0).getID());
    assertEquals(1, pendingRange.get(0).getDeliveredTimes());
    assertEquals("xpendeing-consumer", pendingRange.get(0).getConsumerName());

    // Sleep for 1000ms so we can claim events pending for more than 500ms
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    List<StreamEntry> claimRange = jedis.xclaim("xpendeing-stream", "xpendeing-group",
      "xpendeing-consumer2", 500, 0, 0, false, id1);
    assertEquals(1, claimRange.size());

    // Deleted events should return as null on XClaim
    assertEquals(1, jedis.xdel("xpendeing-stream", id1));
    List<StreamEntry> claimRangeDel = jedis.xclaim("xpendeing-stream", "xpendeing-group",
      "xpendeing-consumer2", 0, 0, 0, false, id1);
    assertEquals(1, claimRangeDel.size());
    assertNull(claimRangeDel.get(0));

    Long pendingMessageNum = jedis.xgroupDelConsumer("xpendeing-stream", "xpendeing-group",
      "xpendeing-consumer2");
    assertEquals(1L, pendingMessageNum.longValue());
  }

  @Test
  public void xpendingWithParams() {
    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    StreamEntryID id1 = jedis.xadd("xpendeing-stream", null, map);

    assertEquals("OK", jedis.xgroupCreate("xpendeing-stream", "xpendeing-group", null, false));

    Entry<String, StreamEntryID> streamQeury1 = new AbstractMap.SimpleImmutableEntry<>(
            "xpendeing-stream", StreamEntryID.UNRECEIVED_ENTRY);

    // Read the event from Stream put it on pending
    List<Entry<String, List<StreamEntry>>> range = jedis.xreadGroup("xpendeing-group",
            "xpendeing-consumer", 1, 1L, false, streamQeury1);
    assertEquals(1, range.size());
    assertEquals(1, range.get(0).getValue().size());
    assertEquals(map, range.get(0).getValue().get(0).getFields());

    // Get the pending event
    List<StreamPendingEntry> pendingRange = jedis.xpending("xpendeing-stream", "xpendeing-group",
            new XPendingParams().count(3).consumer("xpendeing-consumer"));
    assertEquals(1, pendingRange.size());
    assertEquals(id1, pendingRange.get(0).getID());
    assertEquals(1, pendingRange.get(0).getDeliveredTimes());
    assertEquals("xpendeing-consumer", pendingRange.get(0).getConsumerName());

    // Without consumer
    pendingRange = jedis.xpending("xpendeing-stream", "xpendeing-group", new XPendingParams().count(3));
    assertEquals(1, pendingRange.size());
    assertEquals(id1, pendingRange.get(0).getID());
    assertEquals(1, pendingRange.get(0).getDeliveredTimes());
    assertEquals("xpendeing-consumer", pendingRange.get(0).getConsumerName());

    // with idle
    pendingRange = jedis.xpending("xpendeing-stream", "xpendeing-group",
      new XPendingParams().idle(Duration.ofMinutes(1).toMillis()).count(3));
    assertEquals(0, pendingRange.size());
  }

  @Test
  public void xclaimWithParams() {
    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    jedis.xadd("xpendeing-stream", null, map);

    assertEquals("OK", jedis.xgroupCreate("xpendeing-stream", "xpendeing-group", null, false));

    // Read the event from Stream put it on pending
    jedis.xreadGroup("xpendeing-group", "xpendeing-consumer", 1, 1L, false,
            new AbstractMap.SimpleImmutableEntry<>("xpendeing-stream", StreamEntryID.UNRECEIVED_ENTRY));

    // Get the pending event
    List<StreamPendingEntry> pendingRange = jedis.xpending("xpendeing-stream", "xpendeing-group",
            null, null, 3, "xpendeing-consumer");
    // Sleep for 100ms so we can claim events pending for more than 50ms
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    List<StreamEntry> streamEntrys = jedis.xclaim("xpendeing-stream", "xpendeing-group",
            "xpendeing-consumer2", 50, XClaimParams.xClaimParams().idle(0).retryCount(0),
            pendingRange.get(0).getID());
    assertEquals(1, streamEntrys.size());
    assertEquals(pendingRange.get(0).getID(), streamEntrys.get(0).getID());
    assertEquals("v1", streamEntrys.get(0).getFields().get("f1"));
  }

  @Test
  public void xclaimJustId() {
    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    jedis.xadd("xpendeing-stream", null, map);

    assertEquals("OK", jedis.xgroupCreate("xpendeing-stream", "xpendeing-group", null, false));

    // Read the event from Stream put it on pending
    jedis.xreadGroup("xpendeing-group", "xpendeing-consumer", 1, 1L, false,
      new AbstractMap.SimpleImmutableEntry<>("xpendeing-stream", StreamEntryID.UNRECEIVED_ENTRY));

    // Get the pending event
    List<StreamPendingEntry> pendingRange = jedis.xpending("xpendeing-stream", "xpendeing-group",
      null, null, 3, "xpendeing-consumer");
    // Sleep for 100ms so we can claim events pending for more than 50ms
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    List<StreamEntryID> streamEntryIDS = jedis.xclaimJustId("xpendeing-stream", "xpendeing-group",
      "xpendeing-consumer2", 50, XClaimParams.xClaimParams().idle(0).retryCount(0),
      pendingRange.get(0).getID());
    assertEquals(1, streamEntryIDS.size());
    assertEquals(pendingRange.get(0).getID(), streamEntryIDS.get(0));
  }

  @Test
  public void xautoclaim() {
    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    jedis.xadd("xpending-stream", null, map);

    assertEquals("OK", jedis.xgroupCreate("xpending-stream", "xpending-group", null, false));

    // Read the event from Stream put it on pending
    jedis.xreadGroup("xpending-group", "xpending-consumer", 1, 1L, false,
            new AbstractMap.SimpleImmutableEntry<>("xpending-stream", StreamEntryID.UNRECEIVED_ENTRY));

    // Get the pending event
    List<StreamPendingEntry> pendingRange = jedis.xpending("xpending-stream", "xpending-group",
            null, null, 3, "xpending-consumer");
    // Sleep for 100ms so we can auto claim events pending for more than 50ms
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // Auto claim pending events to different consumer
    Map.Entry<StreamEntryID, List<StreamEntry>> streamEntrys = jedis.xautoclaim("xpending-stream", "xpending-group",
            "xpending-consumer2", 50, new StreamEntryID(), new XAutoClaimParams().count(1));
    assertEquals(1, streamEntrys.getValue().size());
    assertEquals(pendingRange.get(0).getID(), streamEntrys.getValue().get(0).getID());
    assertEquals("v1", streamEntrys.getValue().get(0).getFields().get("f1"));
  }

  @Test
  public void xautoclaimBinary() {
    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    jedis.xadd("xpending-stream", null, map);

    assertEquals("OK", jedis.xgroupCreate("xpending-stream", "xpending-group", null, false));

    // Read the event from Stream put it on pending
    jedis.xreadGroup("xpending-group", "xpending-consumer", 1, 1L, false,
            new AbstractMap.SimpleImmutableEntry<>("xpending-stream", StreamEntryID.UNRECEIVED_ENTRY));

    // Get the pending event
    List<StreamPendingEntry> pendingRange = jedis.xpending("xpending-stream", "xpending-group",
            null, null, 3, "xpending-consumer");
    // Sleep for 100ms so we can auto claim events pending for more than 50ms
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // Auto claim pending events to different consumer
    List<Object> streamEntrys = jedis.xautoclaim(SafeEncoder.encode("xpending-stream"),
            SafeEncoder.encode("xpending-group"), SafeEncoder.encode("xpending-consumer2"),
            50, SafeEncoder.encode(new StreamEntryID().toString()), new XAutoClaimParams().count(1));
    Map.Entry<StreamEntryID, List<StreamEntry>> res = BuilderFactory.STREAM_AUTO_CLAIM_RESPONSE.build(streamEntrys);
    assertEquals(1, res.getValue().size());
    assertEquals(pendingRange.get(0).getID(), res.getValue().get(0).getID());
    assertEquals("v1", res.getValue().get(0).getFields().get("f1"));
  }

  @Test
  public void xautoclaimJustId() {
    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    jedis.xadd("xpending-stream", null, map);

    assertEquals("OK", jedis.xgroupCreate("xpending-stream", "xpending-group", null, false));

    // Read the event from Stream put it on pending
    jedis.xreadGroup("xpending-group", "xpending-consumer", 1, 1L, false,
            new AbstractMap.SimpleImmutableEntry<>("xpending-stream", StreamEntryID.UNRECEIVED_ENTRY));

    // Get the pending event
    List<StreamPendingEntry> pendingRange = jedis.xpending("xpending-stream", "xpending-group",
            null, null, 3, "xpending-consumer");
    // Sleep for 100ms so we can auto claim events pending for more than 50ms
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // Auto claim pending events to different consumer
    Map.Entry<StreamEntryID, List<StreamEntryID>> streamEntrys = jedis.xautoclaimJustId("xpending-stream", "xpending-group",
            "xpending-consumer2", 50, new StreamEntryID(), new XAutoClaimParams().count(1));
    assertEquals(1, streamEntrys.getValue().size());
    assertEquals(pendingRange.get(0).getID().getTime(), streamEntrys.getValue().get(0).getTime());
    assertEquals(pendingRange.get(0).getID().getSequence(), streamEntrys.getValue().get(0).getSequence());
  }

  @Test
  public void xautoclaimJustIdBinary() {
    Map<String, String> map = new HashMap<>();
    map.put("f1", "v1");
    jedis.xadd("xpending-stream", null, map);

    assertEquals("OK", jedis.xgroupCreate("xpending-stream", "xpending-group", null, false));

    // Read the event from Stream put it on pending
    jedis.xreadGroup("xpending-group", "xpending-consumer", 1, 1L, false,
            new AbstractMap.SimpleImmutableEntry<>("xpending-stream", StreamEntryID.UNRECEIVED_ENTRY));

    // Get the pending event
    List<StreamPendingEntry> pendingRange = jedis.xpending("xpending-stream", "xpending-group",
            null, null, 3, "xpending-consumer");
    // Sleep for 100ms so we can auto claim events pending for more than 50ms
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // Auto claim pending events to different consumer
    List<Object> streamEntrys = jedis.xautoclaimJustId(SafeEncoder.encode("xpending-stream"),
            SafeEncoder.encode("xpending-group"), SafeEncoder.encode("xpending-consumer2"),
            50, SafeEncoder.encode(new StreamEntryID().toString()), new XAutoClaimParams().count(1));
    Map.Entry<StreamEntryID, List<StreamEntryID>> res = BuilderFactory.STREAM_AUTO_CLAIM_ID_RESPONSE.build(streamEntrys);
    assertEquals(1, res.getValue().size());
    assertEquals(pendingRange.get(0).getID().getTime(), res.getValue().get(0).getTime());
    assertEquals(pendingRange.get(0).getID().getSequence(), res.getValue().get(0).getSequence());
  }

  @Test
  public void xinfo() throws InterruptedException {

    final String STREAM_NAME = "xadd-stream1";
    final String F1 = "f1";
    final String V1 = "v1";
    final String V2 = "v2";
    final String G1 = "G1";
    final String G2 = "G2";
    final String MY_CONSUMER = "myConsumer";
    final String MY_CONSUMER2 = "myConsumer2";

    Map<String, String> map1 = new HashMap<>();
    map1.put(F1, V1);
    StreamEntryID id1 = jedis.xadd(STREAM_NAME, null, map1);
    map1.put(F1, V2);
    StreamEntryID id2 = jedis.xadd(STREAM_NAME, null, map1);
    assertNotNull(id1);
    StreamInfo streamInfo = jedis.xinfoStream(STREAM_NAME);
    assertNotNull(id2);

    jedis.xgroupCreate(STREAM_NAME, G1, StreamEntryID.LAST_ENTRY, false);
    Entry<String, StreamEntryID> streamQeury11 = new AbstractMap.SimpleImmutableEntry<>(
        STREAM_NAME, new StreamEntryID("0-0"));
    jedis.xreadGroup(G1, MY_CONSUMER, 1, 0, false, streamQeury11);

    Thread.sleep(1);

    List<StreamGroupInfo> groupInfo = jedis.xinfoGroup(STREAM_NAME);
    List<StreamConsumersInfo> consumersInfo = jedis.xinfoConsumers(STREAM_NAME, G1);

    // Stream info test
    assertEquals(2L, streamInfo.getStreamInfo().get(LENGTH));
    assertEquals(1L, streamInfo.getStreamInfo().get(RADIX_TREE_KEYS));
    assertEquals(2L, streamInfo.getStreamInfo().get(RADIX_TREE_NODES));
    assertEquals(0L, streamInfo.getStreamInfo().get(GROUPS));
    assertEquals(V1, ((StreamEntry) streamInfo.getStreamInfo().get(FIRST_ENTRY)).getFields()
        .get(F1));
    assertEquals(V2, ((StreamEntry) streamInfo.getStreamInfo().get(LAST_ENTRY)).getFields().get(F1));
    assertEquals(id2, streamInfo.getStreamInfo().get(LAST_GENERATED_ID));

    // Using getters
    assertEquals(2, streamInfo.getLength());
    assertEquals(1, streamInfo.getRadixTreeKeys());
    assertEquals(2, streamInfo.getRadixTreeNodes());
    assertEquals(0, streamInfo.getGroups());
    assertEquals(V1, streamInfo.getFirstEntry().getFields().get(F1));
    assertEquals(V2, streamInfo.getLastEntry().getFields().get(F1));
    assertEquals(id2, streamInfo.getLastGeneratedId());

    // Group info test
    assertEquals(1, groupInfo.size());
    assertEquals(G1, groupInfo.get(0).getGroupInfo().get(NAME));
    assertEquals(1L, groupInfo.get(0).getGroupInfo().get(CONSUMERS));
    assertEquals(0L, groupInfo.get(0).getGroupInfo().get(PENDING));
    assertEquals(id2, groupInfo.get(0).getGroupInfo().get(LAST_DELIVERED));

    // Using getters
    assertEquals(1, groupInfo.size());
    assertEquals(G1, groupInfo.get(0).getName());
    assertEquals(1, groupInfo.get(0).getConsumers());
    assertEquals(0, groupInfo.get(0).getPending());
    assertEquals(id2, groupInfo.get(0).getLastDeliveredId());

    // Consumer info test
    assertEquals(MY_CONSUMER,
      consumersInfo.get(0).getConsumerInfo().get(redis.clients.jedis.StreamConsumersInfo.NAME));
    assertEquals(0L, consumersInfo.get(0).getConsumerInfo().get(StreamConsumersInfo.PENDING));
    assertTrue((Long) consumersInfo.get(0).getConsumerInfo().get(IDLE) > 0);

    // Using getters
    assertEquals(MY_CONSUMER, consumersInfo.get(0).getName());
    assertEquals(0L, consumersInfo.get(0).getPending());
    assertTrue(consumersInfo.get(0).getIdle() > 0);

    // test with more groups and consumers
    jedis.xgroupCreate(STREAM_NAME, G2, StreamEntryID.LAST_ENTRY, false);
    jedis.xreadGroup(G1, MY_CONSUMER2, 1, 0, false, streamQeury11);
    jedis.xreadGroup(G2, MY_CONSUMER, 1, 0, false, streamQeury11);
    jedis.xreadGroup(G2, MY_CONSUMER2, 1, 0, false, streamQeury11);

    List<StreamGroupInfo> manyGroupsInfo = jedis.xinfoGroup(STREAM_NAME);
    List<StreamConsumersInfo> manyConsumersInfo = jedis.xinfoConsumers(STREAM_NAME, G2);

    assertEquals(2, manyGroupsInfo.size());
    assertEquals(2, manyConsumersInfo.size());

    // Not existing key - redis cli return error so we expect exception
    try {
      jedis.xinfoStream("random");
      fail("Command should fail");
    } catch (JedisException e) {
      assertEquals("ERR no such key", e.getMessage());
    }

  }

  @Test
  public void xinfoBinary() throws InterruptedException {

    final String STREAM_NAME = "xadd-stream1";
    final String F1 = "f1";
    final String V1 = "v1";
    final String V2 = "v2";
    final String G1 = "G1";
    final String G2 = "G2";
    final String MY_CONSUMER = "myConsumer";
    final String MY_CONSUMER2 = "myConsumer2";

    Map<String, String> map1 = new HashMap<>();
    map1.put(F1, V1);
    StreamEntryID id1 = jedis.xadd(STREAM_NAME, null, map1);
    map1.put(F1, V2);
    StreamEntryID id2 = jedis.xadd(STREAM_NAME, null, map1);
    assertNotNull(id1);
    StreamInfo streamInfo = jedis.xinfoStream(SafeEncoder.encode(STREAM_NAME));
    assertNotNull(id2);

    jedis.xgroupCreate(STREAM_NAME, G1, StreamEntryID.LAST_ENTRY, false);
    Entry<String, StreamEntryID> streamQeury11 = new AbstractMap.SimpleImmutableEntry<>(
        STREAM_NAME, new StreamEntryID("0-0"));
    jedis.xreadGroup(G1, MY_CONSUMER, 1, 0, false, streamQeury11);

    Thread.sleep(1);

    List<StreamGroupInfo> groupInfo = jedis.xinfoGroup(SafeEncoder.encode(STREAM_NAME));
    List<StreamConsumersInfo> consumersInfo = jedis.xinfoConsumers(SafeEncoder.encode(STREAM_NAME),
      SafeEncoder.encode(G1));

    // Stream info test
    assertEquals(2L, streamInfo.getStreamInfo().get(LENGTH));
    assertEquals(1L, streamInfo.getStreamInfo().get(RADIX_TREE_KEYS));
    assertEquals(2L, streamInfo.getStreamInfo().get(RADIX_TREE_NODES));
    assertEquals(0L, streamInfo.getStreamInfo().get(GROUPS));
    assertEquals(V1, ((StreamEntry) streamInfo.getStreamInfo().get(FIRST_ENTRY)).getFields()
        .get(F1));
    assertEquals(V2, ((StreamEntry) streamInfo.getStreamInfo().get(LAST_ENTRY)).getFields().get(F1));
    assertEquals(id2, streamInfo.getStreamInfo().get(LAST_GENERATED_ID));

    // Group info test
    assertEquals(1, groupInfo.size());
    assertEquals(G1, groupInfo.get(0).getGroupInfo().get(NAME));
    assertEquals(1L, groupInfo.get(0).getGroupInfo().get(CONSUMERS));
    assertEquals(0L, groupInfo.get(0).getGroupInfo().get(PENDING));
    assertEquals(id2, groupInfo.get(0).getGroupInfo().get(LAST_DELIVERED));

    // Consumer info test
    assertEquals(MY_CONSUMER,
      consumersInfo.get(0).getConsumerInfo().get(redis.clients.jedis.StreamConsumersInfo.NAME));
    assertEquals(0L, consumersInfo.get(0).getConsumerInfo().get(StreamConsumersInfo.PENDING));
    assertTrue((Long) consumersInfo.get(0).getConsumerInfo().get(IDLE) > 0);

    // test with more groups and consumers
    jedis.xgroupCreate(STREAM_NAME, G2, StreamEntryID.LAST_ENTRY, false);
    jedis.xreadGroup(G1, MY_CONSUMER2, 1, 0, false, streamQeury11);
    jedis.xreadGroup(G2, MY_CONSUMER, 1, 0, false, streamQeury11);
    jedis.xreadGroup(G2, MY_CONSUMER2, 1, 0, false, streamQeury11);

    List<StreamGroupInfo> manyGroupsInfo = jedis.xinfoGroup(STREAM_NAME);
    List<StreamConsumersInfo> manyConsumersInfo = jedis.xinfoConsumers(STREAM_NAME, G2);

    assertEquals(2, manyGroupsInfo.size());
    assertEquals(2, manyConsumersInfo.size());

    // Not existing key - redis cli return error so we expect exception
    try {
      jedis.xinfoStream(SafeEncoder.encode("random"));
      fail("Command should fail");
    } catch (JedisException e) {
      assertEquals("ERR no such key", e.getMessage());
    }

  }

  @Test
  public void pipeline() {
    Map<String, String> map = new HashMap<>();
    map.put("a", "b");
    Pipeline p = jedis.pipelined();
    Response<StreamEntryID> id1 = p.xadd("stream1", StreamEntryID.NEW_ENTRY, map);
    Response<StreamEntryID> id2 = p.xadd("stream1", StreamEntryID.NEW_ENTRY, map);
    Response<List<StreamEntry>> results = p.xrange("stream1", null, null, 2);
    p.sync();

    List<StreamEntry> entries = results.get();
    assertEquals(2, entries.size());
    assertEquals(id1.get(), entries.get(0).getID());
    assertEquals(map, entries.get(0).getFields());
    assertEquals(id2.get(), entries.get(1).getID());
    assertEquals(map, entries.get(1).getFields());

    p = jedis.pipelined();
    Response<List<StreamEntry>> results2 = p.xrevrange("stream1", null, id1.get(), 2);
    p.sync();
    assertEquals(2, results2.get().size());
  }

  @Test
  public void transaction() {
    Map<String, String> map = new HashMap<>();
    map.put("a", "b");
    Transaction t = jedis.multi();
    Response<StreamEntryID> id1 = t.xadd("stream1", StreamEntryID.NEW_ENTRY, map);
    Response<StreamEntryID> id2 = t.xadd("stream1", StreamEntryID.NEW_ENTRY, map);
    Response<List<StreamEntry>> results = t.xrange("stream1", null, null, 2);
    t.exec();

    List<StreamEntry> entries = results.get();
    assertEquals(2, entries.size());
    assertEquals(id1.get(), entries.get(0).getID());
    assertEquals(map, entries.get(0).getFields());
    assertEquals(id2.get(), entries.get(1).getID());
    assertEquals(map, entries.get(1).getFields());
  }
}
