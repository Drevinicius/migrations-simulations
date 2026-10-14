package simulations;

import org.cloudsimplus.schedulers.MipsShare;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.vms.Vm;

/**
 * Correção para a verificação de disponibilidade de CPU
 * durante a entrada de uma VM em um Host por migração.
 *
 * O CloudSim Plus pode contabilizar temporariamente os MIPS
 * antigos da VM de origem como se já fossem uma alocação de
 * migração no Host destino.
 *
 * Isso pode inflar artificialmente o uso de CPU do Host
 * durante a chamada de isSuitableForVmInternal().
 *
 * Esta classe corrige apenas essa verificação, sem alterar
 * efetivamente as alocações da VM.
 */
public class FixedVmSchedulerTimeShared
		extends VmSchedulerTimeShared {

	@Override
	protected boolean isSuitableForVmInternal(
			final Vm vm,
			final MipsShare requestedMips) {

		final double totalRequestedMips = requestedMips.totalMips();

		double availableMips = getTotalAvailableMips();

		/*
		 * Se a própria VM que está sendo avaliada já aparece
		 * na lista de VMs migrando para este Host, ela já foi
		 * contabilizada por getTotalAvailableMips().
		 *
		 * Removemos somente essa contabilização temporária da
		 * própria VM para evitar dupla contabilização.
		 */
		if (getHost()
				.getVmsMigratingIn()
				.contains(vm)) {

			final double allocatedMips = getAllocatedMips(vm).totalMips();

			final double migrationPercent = mipsPercentToRequest(vm);

			if (allocatedMips > 0
					&& migrationPercent > 0) {

				final double vmAccountedMips = allocatedMips
						/ migrationPercent;

				availableMips += vmAccountedMips;
			}
		}

		final boolean enoughPes = getHost()
				.getWorkingPesNumber() >= requestedMips.pes();

		final boolean enoughMips = availableMips >= totalRequestedMips;

		return enoughPes && enoughMips;
	}
}