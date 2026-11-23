package br.com.amigosecreto.group;

import br.com.amigosecreto.common.ConflictException;

/**
 * O grupo foi finalizado e ficou somente leitura (FR-018). É lançada logo depois do lock, antes de qualquer
 * outra escrita, então os serviços a declaram em {@code noRollbackFor}: se foi a própria escrita que
 * finalizou o grupo ({@link GroupLock}), a finalização fica gravada e o aviso {@code group-finished} sai.
 */
public class GroupFinishedException extends ConflictException {

    public GroupFinishedException() {
        super("group-finished", "Este grupo foi finalizado.");
    }
}
