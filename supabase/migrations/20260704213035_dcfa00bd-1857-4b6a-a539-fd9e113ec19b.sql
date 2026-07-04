-- Revoke public execute on SECURITY DEFINER functions
REVOKE ALL ON FUNCTION public.has_role(uuid, app_role) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.grant_admin_on_signup() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_tester_count() FROM PUBLIC, anon, authenticated;
-- has_role must remain callable by authenticated for RLS policy use
GRANT EXECUTE ON FUNCTION public.has_role(uuid, app_role) TO authenticated;