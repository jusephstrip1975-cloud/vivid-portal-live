
CREATE TABLE public.tester_emails (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

GRANT INSERT ON public.tester_emails TO anon;
GRANT INSERT ON public.tester_emails TO authenticated;
GRANT ALL ON public.tester_emails TO service_role;

ALTER TABLE public.tester_emails ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Anyone can register as tester"
  ON public.tester_emails
  FOR INSERT
  TO anon, authenticated
  WITH CHECK (
    email IS NOT NULL
    AND length(email) <= 255
    AND email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
  );

CREATE OR REPLACE FUNCTION public.get_tester_count()
RETURNS INTEGER
LANGUAGE SQL
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT COUNT(*)::INTEGER FROM public.tester_emails;
$$;

GRANT EXECUTE ON FUNCTION public.get_tester_count() TO anon, authenticated;
