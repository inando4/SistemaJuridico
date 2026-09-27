-- Solo para la base desechable sj-auditoria-20260926.
CREATE FUNCTION audit_fail_cancel_for_probe() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.entity_id = '09323cc0-b6bf-4973-be32-2f58dccff205'::uuid AND NEW.action IN ('CANCEL', 'RESTORE') THEN
    RAISE EXCEPTION 'Fallo de persistencia inyectado por auditoría exploratoria';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER audit_fail_cancel_for_probe BEFORE INSERT ON audit_event
FOR EACH ROW EXECUTE FUNCTION audit_fail_cancel_for_probe();
SELECT active,version,(SELECT count(*) FROM audit_event WHERE entity_id=p.id AND action='CANCEL') AS cancel_events
FROM pending_task p WHERE id='09323cc0-b6bf-4973-be32-2f58dccff205';
