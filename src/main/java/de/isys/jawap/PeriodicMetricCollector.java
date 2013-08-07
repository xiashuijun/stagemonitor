package de.isys.jawap;

import de.isys.jawap.collectors.CpuUtilisationWatch;
import de.isys.jawap.collectors.ThreadPoolMetricsCollector;
import de.isys.jawap.facade.PerformanceMeasuringFacade;
import de.isys.jawap.model.PeriodicPerformanceData;
import de.isys.jawap.model.ThreadPoolMetrics;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.webstage.shop.core.configuration.DBProperties;

import javax.annotation.Resource;
import java.util.concurrent.atomic.AtomicLong;

public class PeriodicMetricCollector {
	private static Log log = LogFactory.getLog(PeriodicMetricCollector.class);

	@Resource
	private ThreadPoolMetricsCollector serverCollector;
	@Resource
	private ThreadPoolMetricsCollector springThreadPoolMetricsCollectorImpl;
	@Resource
	private ThreadPoolMetricsCollector springScheduledThreadPoolMetricsCollectorImpl;
	@Resource
	private PerformanceMeasuringFacade performanceMeasuringFacade;
	@Resource
	private PerformanceMonitorFilter performanceMonitorFilter;
	private CpuUtilisationWatch cpuUtilisationWatch;
	private AtomicLong lastMetricsCollection = new AtomicLong(0);

	public PeriodicMetricCollector() {
		cpuUtilisationWatch = new CpuUtilisationWatch();
		cpuUtilisationWatch.start();
	}

	@Scheduled(fixedRate = 5 * 1000)
	public void collectPerformanceDataAndWriteToDb() {
		long timeSinceLastUpdate = System.currentTimeMillis() - lastMetricsCollection.longValue();
		if (timeSinceLastUpdate >= DBProperties.PERIODIC_PERFORMANCE_METRICS_COLLECTION_ITERVAL.getIntegerValue().longValue()) {

			PeriodicPerformanceData periodicPerformanceData = new PeriodicPerformanceData(performanceMonitorFilter.
					getPerformanceMeasurementSession());


			collectThreadPoolMetricsIfEnabled(periodicPerformanceData);
			collectHeapMetricsIfEnabled(periodicPerformanceData);
			collectCpuMetricsIfEnabled(periodicPerformanceData);

			if (isMeasuringEnabled()) {
				performanceMeasuringFacade.save(periodicPerformanceData);
			}

			lastMetricsCollection.set(System.currentTimeMillis());
		}
	}

	private boolean isMeasuringEnabled() {
		return DBProperties.PERIODIC_PERFORMANCE_THEAD_POOL_METRICS.getBoolean()
				|| DBProperties.PERIODIC_PERFORMANCE_HEAP_UTILISATION.getBoolean()
				|| DBProperties.PERIODIC_PERFORMANCE_CPU_UTILISATION.getBoolean();
	}


	private void collectThreadPoolMetricsIfEnabled(PeriodicPerformanceData periodicPerformanceData) {
		if (DBProperties.PERIODIC_PERFORMANCE_THEAD_POOL_METRICS.getBoolean()) {
			try {
				periodicPerformanceData.setAppServerThreadPoolMetrics(createThreadPoolMetrics(serverCollector));
			} catch (RuntimeException e) {
				log.error(e.getMessage(), e);
			}

			periodicPerformanceData.setSpringThreadPoolMetrics(createThreadPoolMetrics(
					springThreadPoolMetricsCollectorImpl));

			periodicPerformanceData.setSpringScheduledThreadPoolMetrics(createThreadPoolMetrics(
					springScheduledThreadPoolMetricsCollectorImpl));
		}
	}

	private ThreadPoolMetrics createThreadPoolMetrics(ThreadPoolMetricsCollector metricsCollector) {
		ThreadPoolMetrics poolMetrics = new ThreadPoolMetrics();
		poolMetrics.setMaxPoolSize(metricsCollector.getMaxPoolSize());
		poolMetrics.setThreadPoolNumActiveThreads(metricsCollector.getThreadPoolNumActiveThreads());
		poolMetrics.setThreadPoolNumTasksPending(metricsCollector.getThreadPoolNumTasksPending());
		poolMetrics.setThreadPoolSize(metricsCollector.getThreadPoolSize());
		return poolMetrics;
	}

	private void collectHeapMetricsIfEnabled(PeriodicPerformanceData periodicPerformanceData) {
		if (DBProperties.PERIODIC_PERFORMANCE_HEAP_UTILISATION.getBoolean()) {
			periodicPerformanceData.setFreeMemory(Runtime.getRuntime().freeMemory());
			periodicPerformanceData.setTotalMemory(Runtime.getRuntime().totalMemory());
		}
	}

	private void collectCpuMetricsIfEnabled(PeriodicPerformanceData periodicPerformanceData) {
		if (DBProperties.PERIODIC_PERFORMANCE_CPU_UTILISATION.getBoolean()) {
			periodicPerformanceData.setCpuUsagePercent(cpuUtilisationWatch.getCpuUsagePercent());
			cpuUtilisationWatch.start();
		}
	}
}
