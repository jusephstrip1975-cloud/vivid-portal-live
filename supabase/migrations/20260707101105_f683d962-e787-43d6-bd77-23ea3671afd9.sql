-- Lock down SECURITY DEFINER functions from being callable by end users.

-- Trigger function: only the system needs to execute it.
REVOKE ALL ON FUNCTION public.grant_admin_on_signup() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.grant_admin_on_signup() TO service_role;

-- Admin/reporting helper: restrict to service_role only.
REVOKE ALL ON FUNCTION public.get_tester_count() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_tester_count() TO service_role;

-- has_role must remain executable by authenticated users because it is
-- referenced inside RLS policies evaluated as the calling role. Remove
-- access for PUBLIC and anon to reduce the exposure surface.
REVOKE ALL ON FUNCTION public.has_role(uuid, public.app_role) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.has_role(uuid, public.app_role) TO authenticated, service_role;