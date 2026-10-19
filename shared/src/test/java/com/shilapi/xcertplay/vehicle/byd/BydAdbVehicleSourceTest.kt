package com.shilapi.xcertplay.vehicle.byd

import com.shilapi.xcertplay.transport.*
import com.shilapi.xcertplay.vehicle.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BydAdbVehicleSourceTest {
    private val all = VehicleDataConfig(enabled = setOf(VehicleDataFeature.WHEEL_SPEED,VehicleDataFeature.EV_STATUS))
    private fun link() = FakeBydLink().apply {
        float(-1807745016,36f); values[555745336]=4
        float(1246777400,50f); values[1246765118]=200
        float(882901008,30f); values[876609560]=1
    }
    @Test fun disabledSourceNeverConnectsOrStartsReads() {
        val link=link(); val source=BydAdbVehicleSource(VehicleDataConfig(),false,link,{1000},autoSchedule=false)
        assertFalse(source.start()); source.tick()
        assertTrue(link.reads.isEmpty()); assertNull(source.snapshot()); assertNull(source.latestSpeed()); source.close()
    }
    @Test fun dataFlowsFromAdbThroughNormalizedStatusAndPascdWithPhoneSelectors() {
        val link=link(); val source=BydAdbVehicleSource(all,false,link,{1000},autoSchedule=false)
        source.start(); source.requestSpeed(true); source.tick()
        val status=source.snapshot()!!
        assertEquals(setOf(11,21),Iap2VehicleStatus.capabilities(status))
        val fields=com.shilapi.xcertplay.iap2.wire.Iap2ParameterList.parse(
            Iap2VehicleStatus.update(status,setOf(11,21),setOf(11))!!.payload).asList()
        assertEquals(listOf(11,22,24,25),fields.map { it.id })
        assertTrue(PascdEncoder.encode(source.latestSpeed()!!)!!.contains(",C,D,0,1,0.00,10.000*"))
        source.close(); assertNull(source.snapshot()); assertNull(source.latestSpeed())
    }
    @Test fun normalForwardingNeverReadsTyresAndRespectsSubscriptionsAfterPreflight() {
        var now=1000L;val link=link();val source=BydAdbVehicleSource(all,false,link,{now},autoSchedule=false)
        source.start();source.tick();val count=link.reads.size
        now=4000;source.tick();assertEquals(count,link.reads.size)
        now=35000;source.onSubscriptionChanged(true);source.tick();assertTrue(link.reads.size>count)
        assertFalse(link.reads.any { "i32 1016" in it || "i32 1007" in it })
        source.onSubscriptionChanged(false);val stopped=link.reads.size
        now=70000;source.tick();assertEquals(stopped,link.reads.size);source.close()
    }
    @Test fun diagnosticsCanReadWithAllFeaturesOffButNeverReportToPhoneAndStopAtDeadline() {
        var now=1000L;val link=link();val source=BydAdbVehicleSource(VehicleDataConfig(),true,link,{now},autoSchedule=false)
        assertTrue(source.start());source.tick()
        assertTrue(link.reads.any { "i32 1016" in it });assertNull(source.snapshot());assertNull(source.latestSpeed())
        assertNull(source.awaitSnapshot(0));assertNull(source.awaitSpeed(0))
        now=601000;source.tick();assertTrue(link.cancelled);assertTrue(source.diagnostics().contains("采样已停止"))
    }
    @Test fun staleNegativeClockAndFailedReadingsCannotReuseOldData() {
        var now=1000L;val link=link();val source=BydAdbVehicleSource(all,false,link,{now},autoSchedule=false)
        source.start();source.requestSpeed(true);source.tick();assertNotNull(source.latestSpeed())
        now=3001;assertNull(source.latestSpeed());assertNotNull(source.snapshot())
        now=999;assertNull(source.snapshot());assertNull(source.latestSpeed())
        now=91001;assertNull(source.snapshot())
        link.accessible=false;source.tick();assertNull(source.snapshot());assertNull(source.latestSpeed());source.close()
    }
    @Test fun connectionAndTransportFailuresInvalidateImmediatelyAndRecover() {
        var now=1000L;val link=link();val source=BydAdbVehicleSource(all,false,link,{now},autoSchedule=false)
        source.start();source.requestSpeed(true);source.tick()
        val speed="service call autoservice 7 i32 1013 i32 -1807745016"
        now=1250;link.failures+=speed;source.tick();assertNull(source.latestSpeed());assertNull(source.snapshot())
        now=32000;link.failures.clear();source.tick();assertNotNull(source.latestSpeed())
        source.onSubscriptionChanged(true);source.tick();assertNotNull(source.snapshot());source.close()
    }
    @Test fun slowSpeedSamplesAreNotAdvertisedAsFresh() {
        var now=1000L;val link=link();val source=BydAdbVehicleSource(all,false,link,{now},autoSchedule=false)
        link.fallback={command->if(command.startsWith("getprop"))"CANFD" else {
            now+=1500
            val id=command.substringAfterLast(' ').toInt();FakeBydLink.parcel(link.values[id] ?: -1)
        }}
        source.start();source.requestSpeed(true);source.tick();assertNull(source.latestSpeed());source.close()
    }
    @Test fun closingAnInFlightWorkerCancelsWithoutWaitingForShellAndCannotPublishLate() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val link=link()
        link.fallback={entered.countDown();release.await(5,TimeUnit.SECONDS);"CANFD"}
        val source=BydAdbVehicleSource(all,false,link,{1000})
        source.start();assertTrue(entered.await(2,TimeUnit.SECONDS))
        val before=System.nanoTime();source.close()
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)<500)
        assertTrue(link.cancelled);release.countDown();assertNull(source.snapshot());assertNull(source.latestSpeed())
    }
}
