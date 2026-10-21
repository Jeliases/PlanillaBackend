package pe.Planilla.config;

import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionListener;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Auditoría de todos los campos (pedido de la empresa): cada cambio en una tabla auditada guarda una copia de la fila
 * con el usuario y la hora. Las tablas se marcan con @Audited; su historial se lee en /api/auditoria.
 */
public class AuditoriaConfig {

    /** Una "revisión" = una transacción que cambió algo. Guarda quién la hizo. */
    @Data
    @Entity
    @Table(name = "auditoria_revision")
    @RevisionEntity(UsuarioRevisionListener.class)
    public static class Revision {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @RevisionNumber
        private int id;

        @RevisionTimestamp
        private long marcaTiempo;

        private String usuario;
    }

    public static class UsuarioRevisionListener implements RevisionListener {
        @Override
        public void newRevision(Object revisionEntity) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String usuario = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                    ? auth.getName() : "sistema";
            ((Revision) revisionEntity).setUsuario(usuario);
        }
    }
}
