package simulations;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.MipsShare;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.builders.tables.CsvTable;
import org.cloudsimplus.builders.tables.HostHistoryTableBuilder;
import org.cloudsimplus.listeners.VmHostEventInfo;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.vms.HostResourceStats;
import org.cloudsimplus.vms.VmResourceStats;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class Shared {

    public static final class Config {

        public static final class Scheduling {
            public static final int INTERVAL = 1;
        }

        public static final class Host {

            public static final double UNDER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION = 0.1;

            public static final double OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION = 0.8;

            public static final int SEARCH_RETRY_DELAY = 60;

            public static final long BW = 16_000L;

            public static final int MIPS = 3000;

            /*
             * 40 Hosts:
             * 16 cores
             * 64 GB RAM
             */
            public static final long RAM[] = {

                    64_000, 64_000, 64_000, 64_000, 64_000,
                    64_000, 64_000, 64_000, 64_000, 64_000,

                    64_000, 64_000, 64_000, 64_000, 64_000,
                    64_000, 64_000, 64_000, 64_000, 64_000,

                    64_000, 64_000, 64_000, 64_000, 64_000,
                    64_000, 64_000, 64_000, 64_000, 64_000,

                    64_000, 64_000, 64_000, 64_000, 64_000,
                    64_000, 64_000, 64_000, 64_000, 64_000,

                    /*
                     * 30 Hosts:
                     * 32 cores
                     * 128 GB RAM
                     */
                    128_000, 128_000, 128_000, 128_000, 128_000,
                    128_000, 128_000, 128_000, 128_000, 128_000,

                    128_000, 128_000, 128_000, 128_000, 128_000,
                    128_000, 128_000, 128_000, 128_000, 128_000,

                    128_000, 128_000, 128_000, 128_000, 128_000,
                    128_000, 128_000, 128_000, 128_000, 128_000,

                    /*
                     * 20 Hosts:
                     * 64 cores
                     * 256 GB RAM
                     */
                    256_000, 256_000, 256_000, 256_000, 256_000,
                    256_000, 256_000, 256_000, 256_000, 256_000,

                    256_000, 256_000, 256_000, 256_000, 256_000,
                    256_000, 256_000, 256_000, 256_000, 256_000,

                    /*
                     * 10 Hosts:
                     * 128 cores
                     * 512 GB RAM
                     */
                    512_000, 512_000, 512_000, 512_000, 512_000,
                    512_000, 512_000, 512_000, 512_000, 512_000
            };

            public static final int PES[] = {

                    /*
                     * 40 Hosts de 16 cores
                     */
                    16, 16, 16, 16, 16,
                    16, 16, 16, 16, 16,

                    16, 16, 16, 16, 16,
                    16, 16, 16, 16, 16,

                    16, 16, 16, 16, 16,
                    16, 16, 16, 16, 16,

                    16, 16, 16, 16, 16,
                    16, 16, 16, 16, 16,

                    /*
                     * 30 Hosts de 32 cores
                     */
                    32, 32, 32, 32, 32,
                    32, 32, 32, 32, 32,

                    32, 32, 32, 32, 32,
                    32, 32, 32, 32, 32,

                    32, 32, 32, 32, 32,
                    32, 32, 32, 32, 32,

                    /*
                     * 20 Hosts de 64 cores
                     */
                    64, 64, 64, 64, 64,
                    64, 64, 64, 64, 64,

                    64, 64, 64, 64, 64,
                    64, 64, 64, 64, 64,

                    /*
                     * 10 Hosts de 128 cores
                     */
                    128, 128, 128, 128, 128,
                    128, 128, 128, 128, 128
            };

            public static final long STORAGE = 4_000_000;
        }

        public static final class VM {

            public static final int PES[] = {

                    /*
                     * 30 VMs com 1 PE
                     */
                    1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1,

                    /*
                     * 30 VMs com 2 PEs
                     */
                    2, 2, 2, 2, 2,
                    2, 2, 2, 2, 2,
                    2, 2, 2, 2, 2,
                    2, 2, 2, 2, 2,
                    2, 2, 2, 2, 2,
                    2, 2, 2, 2, 2,

                    /*
                     * 30 VMs com 4 PEs
                     */
                    4, 4, 4, 4, 4,
                    4, 4, 4, 4, 4,
                    4, 4, 4, 4, 4,
                    4, 4, 4, 4, 4,
                    4, 4, 4, 4, 4,
                    4, 4, 4, 4, 4,

                    /*
                     * 25 VMs com 8 PEs
                     */
                    8, 8, 8, 8, 8,
                    8, 8, 8, 8, 8,
                    8, 8, 8, 8, 8,
                    8, 8, 8, 8, 8,
                    8, 8, 8, 8, 8,

                    /*
                     * 20 VMs com 16 PEs
                     */
                    16, 16, 16, 16, 16,
                    16, 16, 16, 16, 16,
                    16, 16, 16, 16, 16,
                    16, 16, 16, 16, 16,

                    /*
                     * 10 VMs com 32 PEs
                     */
                    32, 32, 32, 32, 32,
                    32, 32, 32, 32, 32,

                    /*
                     * 5 VMs com 64 PEs
                     */
                    64, 64, 64, 64, 64
            };

            public static final int MIPS = 2000;

            public static final long SIZE = 1000;

            public static final int RAM = 16_000;

            public static final double BW =
                    Config.Host.BW / (double) PES.length;
        }

        public static final class Cloudlet {

            public static final long LENGTH = 20_000;

            public static final long FILESIZE = 300;

            public static final long OUTPUTSIZE = 300;

            public static final double INITIAL_CPU_PERCENTAGE = 0.8;

            public static final double CPU_INCREMENT_PER_SECOND = 0.04;
        }

        public static final class Power {

            public static final double SMALL_STATIC_POWER = 35;

            public static final int SMALL_MAX_POWER = 50;

            public static final double MEDIUM_STATIC_POWER = 70;

            public static final int MEDIUM_MAX_POWER = 100;

            public static final double LARGE_STATIC_POWER = 140;

            public static final int LARGE_MAX_POWER = 200;

            public static final double XLARGE_STATIC_POWER = 280;

            public static final int XLARGE_MAX_POWER = 400;
        }
    }

    public static void showVmAllocatedMips(
            final Vm vm,
            final Host targetHost,
            final double time) {

        final String msg =
                String.format(
                        "# %.2f: %s in %s: total allocated",
                        time,
                        vm,
                        targetHost);

        final MipsShare allocatedMips =
                targetHost
                        .getVmScheduler()
                        .getAllocatedMips(vm);

        final String msg2 =
                allocatedMips.totalMips()
                        == Config.VM.MIPS * 0.9
                        ? " - reduction due to migration overhead"
                        : "";

        System.out.printf(
                "%s %.0f MIPs (divided by %d PEs)%s%n",
                msg,
                allocatedMips.totalMips(),
                allocatedMips.pes(),
                msg2);
    }

    public static void showHostAllocatedMips(
            final double time,
            final Host host) {

        System.out.printf(
                "%.2f: %s allocated %.2f MIPS from %.2f total capacity%n",
                time,
                host,
                host.getTotalAllocatedMips(),
                host.getTotalMipsCapacity());
    }

    public static void printHostStateHistory(
            final Host host) {

        new HostHistoryTableBuilder(host)
                .setTitle(host.toString())
                .build();
    }

    public static Cloudlet createCloudlet(
            final Vm vm,
            final DatacenterBroker broker,
            final UtilizationModel cpuUtilizationModel) {

        final UtilizationModel utilizationModelFull =
                new UtilizationModelFull();

        final Cloudlet cloudlet =
                new CloudletSimple(
                        Config.Cloudlet.LENGTH,
                        (int) vm.getPesNumber())
                        .setFileSize(
                                Config.Cloudlet.FILESIZE)
                        .setOutputSize(
                                Config.Cloudlet.OUTPUTSIZE)
                        .setUtilizationModelRam(
                                utilizationModelFull)
                        .setUtilizationModelBw(
                                utilizationModelFull)
                        .setUtilizationModelCpu(
                                cpuUtilizationModel);

        broker.bindCloudletToVm(
                cloudlet,
                vm);

        return cloudlet;
    }

    public static Vm createVm(
            final int pes) {

        final Vm vm =
                new VmSimple(
                        Config.VM.MIPS,
                        pes);

        vm.setRam(Config.VM.RAM)
                .setBw((long) Config.VM.BW)
                .setSize(Config.VM.SIZE)
                .setCloudletScheduler(
                        new CloudletSchedulerTimeShared());

        return vm;
    }

    public static UtilizationModelDynamic createCpuUtilizationModel(
            double initialCpuUsagePercent,
            double maxCpuUsagePercentage) {

        if (maxCpuUsagePercentage
                < initialCpuUsagePercent) {

            throw new IllegalArgumentException(
                    "Max CPU usage must be equal or greater than the initial CPU usage.");
        }

        initialCpuUsagePercent =
                Math.min(
                        initialCpuUsagePercent,
                        1);

        maxCpuUsagePercentage =
                Math.min(
                        maxCpuUsagePercentage,
                        1);

        final UtilizationModelDynamic utilizationModel;

        if (initialCpuUsagePercent
                < maxCpuUsagePercentage) {

            utilizationModel =
                    new UtilizationModelDynamic(
                            initialCpuUsagePercent)
                            .setUtilizationUpdateFunction(
                                    util ->
                                            util.getUtilization()
                                                    + util.getTimeSpan()
                                                    * Config.Cloudlet.CPU_INCREMENT_PER_SECOND);

        } else {

            utilizationModel =
                    new UtilizationModelDynamic(
                            initialCpuUsagePercent);
        }

        utilizationModel.setMaxResourceUtilization(
                maxCpuUsagePercentage);

        return utilizationModel;
    }

    /**
     * Criação original dos Hosts.
     *
     * Usado pelo BEST FIT.
     *
     * Mantém o VmSchedulerTimeShared original
     * do código do experimento.
     */
    public static Host createHost(
            final int pesNumber,
            final long ram) {

        return createHostInternal(
                pesNumber,
                ram,
                false);
    }

    /**
     * Criação dos Hosts usando o scheduler corrigido.
     *
     * Usado exclusivamente pelo FIRST FIT.
     */
    public static Host createHostWithFixedScheduler(
            final int pesNumber,
            final long ram) {

        return createHostInternal(
                pesNumber,
                ram,
                true);
    }

    /**
     * Implementação compartilhada da criação física do Host.
     *
     * A única diferença entre os dois caminhos é o VmScheduler.
     */
    private static Host createHostInternal(
            final int pesNumber,
            final long ram,
            final boolean useFixedScheduler) {

        final List<Pe> peList =
                createPeList(pesNumber);

        final Host host =
                new HostSimple(
                        ram,
                        Config.Host.BW,
                        Config.Host.STORAGE,
                        peList);

        if (useFixedScheduler) {

            host.setVmScheduler(
                    new FixedVmSchedulerTimeShared());

        } else {

            host.setVmScheduler(
                    new VmSchedulerTimeShared());
        }

        host.enableUtilizationStats();

        host.setStateHistoryEnabled(true);

        final PowerModelHost powerModel;

        if (pesNumber <= 16) {

            powerModel =
                    new PowerModelHostSimple(
                            Config.Power.SMALL_MAX_POWER,
                            Config.Power.SMALL_STATIC_POWER);

        } else if (pesNumber <= 32) {

            powerModel =
                    new PowerModelHostSimple(
                            Config.Power.MEDIUM_MAX_POWER,
                            Config.Power.MEDIUM_STATIC_POWER);

        } else if (pesNumber <= 64) {

            powerModel =
                    new PowerModelHostSimple(
                            Config.Power.LARGE_MAX_POWER,
                            Config.Power.LARGE_STATIC_POWER);

        } else {

            powerModel =
                    new PowerModelHostSimple(
                            Config.Power.XLARGE_MAX_POWER,
                            Config.Power.XLARGE_STATIC_POWER);
        }

        host.setPowerModel(
                powerModel);

        return host;
    }

    public static List<Pe> createPeList(
            final int pesNumber) {

        final List<Pe> list =
                new ArrayList<>(pesNumber);

        for (int i = 0;
                i < pesNumber;
                i++) {

            list.add(
                    new PeSimple(
                            Config.Host.MIPS));
        }

        return list;
    }

    public static void exportPowerConsumptionToCsv(
            final List<Host> hostList,
            final CloudSimPlus simulation,
            final CsvTable csv,
            final String policyName) {

        csv.setTitle(
                "Power Consumption Data - "
                        + policyName
                        + " Migration Policy");

        final PrintStream out =
                csv.getPrintStream();

        out.println(
                "Host,CPUUtilization,PowerConsumption(W),TotalEnergyConsumption(Wh)");

        double totalPower = 0;

        double totalEnergy = 0;

        for (final Host host : hostList) {

            final HostResourceStats cpuStats =
                    host.getCpuUtilizationStats();

            final double utilizationPercentMean =
                    cpuStats.getMean();

            final double watts =
                    host.getPowerModel()
                            .getPower(
                                    utilizationPercentMean);

            final double energyWattHour =
                    watts
                            * (simulation.clock()
                            / 3600.0);

            out.printf(
                    "%d,%.2f,%.2f,%.2f%n",
                    host.getId(),
                    utilizationPercentMean * 100,
                    watts,
                    energyWattHour);

            totalPower += watts;

            totalEnergy += energyWattHour;
        }

        out.printf(
                "Total,,%.2f,%.2f%n",
                totalPower,
                totalEnergy);
    }

    public static void printPowerConsumptionSummary(
            final List<Host> hostList,
            final CloudSimPlus simulation) {

        System.out.println(
                "\n---------- POWER CONSUMPTION SUMMARY ----------");

        double totalPower = 0;

        double totalEnergy = 0;

        for (final Host host : hostList) {

            final HostResourceStats cpuStats =
                    host.getCpuUtilizationStats();

            final double utilizationPercentMean =
                    cpuStats.getMean();

            final double watts =
                    host.getPowerModel()
                            .getPower(
                                    utilizationPercentMean);

            final double energyWattHour =
                    watts
                            * (simulation.clock()
                            / 3600.0);

            System.out.printf(
                    "Host %d - CPU Utilization: %.2f%% - Power: %.2f W - Energy: %.2f Wh%n",
                    host.getId(),
                    utilizationPercentMean * 100,
                    watts,
                    energyWattHour);

            totalPower += watts;

            totalEnergy += energyWattHour;
        }

        System.out.printf(
                "%nTotal Datacenter Power: %.2f W%n",
                totalPower);

        System.out.printf(
                "Total Energy Consumption: %.2f Wh%n",
                totalEnergy);

        System.out.println(
                "------------------------------------------------");
    }

    public static void exportVmStatsToCsv(
            final List<Vm> vmList,
            final String filename)
            throws IOException {

        final PrintStream out =
                new PrintStream(
                        new java.io.File(filename));

        out.println(
                "VM ID,Host ID,VM PEs,CPU Usage Mean (%),Power Consumption (W),Status");

        for (final Vm vm : vmList) {

            String status = "UNKNOWN";

            double cpuUsagePercent = 0.0;

            double power = 0.0;

            int hostId = -1;

            if (vm.getHost() == Host.NULL
                    || !vm.isCreated()) {

                status =
                        "NOT_ALLOCATED";

            } else {

                status =
                        "RUNNING";

                hostId =
                        (int) vm.getHost()
                                .getId();

                final VmResourceStats cpuStats =
                        vm.getCpuUtilizationStats();

                if (cpuStats != null) {

                    final double cpuMean =
                            cpuStats.getMean();

                    if (!Double.isNaN(cpuMean)
                            && !vm.getHost()
                            .getVmCreatedList()
                            .isEmpty()) {

                        cpuUsagePercent =
                                cpuMean * 100;

                        try {

                            final var powerModel =
                                    vm.getHost()
                                            .getPowerModel();

                            if (powerModel != null) {

                                final double hostStaticPower =
                                        powerModel
                                                instanceof PowerModelHostSimple powerModelHost
                                                ? powerModelHost.getStaticPower()
                                                : 0;

                                final double hostStaticPowerByVm =
                                        hostStaticPower
                                                / Math.max(
                                                1,
                                                vm.getHost()
                                                        .getVmCreatedList()
                                                        .size());

                                final double vmRelativeCpuUtilization =
                                        cpuMean
                                                / Math.max(
                                                1,
                                                vm.getHost()
                                                        .getVmCreatedList()
                                                        .size());

                                power =
                                        powerModel.getPower(
                                                vmRelativeCpuUtilization)
                                                - hostStaticPower
                                                + hostStaticPowerByVm;
                            }

                        } catch (final Exception e) {

                            status =
                                    "ERROR: "
                                            + e.getMessage();
                        }
                    }
                }
            }

            out.printf(
                    "%d,%d,%d,%.2f,%.2f,%s%n",
                    vm.getId(),
                    hostId,
                    vm.getPesNumber(),
                    cpuUsagePercent,
                    power,
                    status);
        }

        out.close();

        System.out.println(
                "VM statistics exported to "
                        + filename);
    }

    public static void startMigration(
            final int[] migrationsNumber,
            final CloudSimPlus simulation,
            final VmHostEventInfo info) {

        final Vm vm =
                info.getVm();

        final Host targetHost =
                info.getHost();

        System.out.printf(
                "# %.2f: %s started migrating to %s (you can perform any operation you want here)%n",
                info.getTime(),
                vm,
                targetHost);

        showVmAllocatedMips(
                vm,
                targetHost,
                info.getTime());

        showHostAllocatedMips(
                info.getTime(),
                vm.getHost());

        showHostAllocatedMips(
                info.getTime(),
                targetHost);

        System.out.println();

        migrationsNumber[0]++;

        if (migrationsNumber[0] > 1) {
            return;
        }

        simulation.addOnClockTickListener(
                clock -> {

                    if (clock.getTime() <= 2
                            || (clock.getTime() >= 11
                            && clock.getTime() <= 15)) {

                        showVmAllocatedMips(
                                vm,
                                targetHost,
                                clock.getTime());
                    }
                });
    }

    public static void finishMigration(
            final List<Host> hostList,
            final VmHostEventInfo info) {

        final Host host =
                info.getHost();

        System.out.printf(
                "# %.2f: %s finished migrating to %s (you can perform any operation you want here)%n",
                info.getTime(),
                info.getVm(),
                host);

        System.out.print("\t\t");

        showHostAllocatedMips(
                info.getTime(),
                hostList.get(1));

        System.out.print("\t\t");

        showHostAllocatedMips(
                info.getTime(),
                host);
    }
}