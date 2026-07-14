-- ====================================================================
-- FOCUSFLOW (FOCUS LAUNCHER & PRODUCTIVITY APPMINISTRATOR)
-- SUPABASE DATABASE SCHEMA & POLICIES
-- Copy and paste this script directly into your Supabase SQL Editor.
-- ====================================================================

-- 1. Create Profile Table (Corresponds to UserProfileEntity)
CREATE TABLE IF NOT EXISTS public.profile (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL DEFAULT 'Focus Member',
    email TEXT,
    daily_goal INTEGER NOT NULL DEFAULT 120, -- Focus goal in minutes
    preferred_apps JSONB NOT NULL DEFAULT '[]'::JSONB, -- List of chosen launcher home screen apps (List<String>)
    schedule JSONB NOT NULL DEFAULT '[]'::JSONB, -- Focus time blocking schedule (List<ScheduleEntry>)
    notifications BOOLEAN NOT NULL DEFAULT TRUE,
    dark_mode BOOLEAN NOT NULL DEFAULT TRUE,
    privacy_mode BOOLEAN NOT NULL DEFAULT FALSE,
    photo_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Enable Row Level Security (RLS) for Profile
ALTER TABLE public.profile ENABLE ROW LEVEL SECURITY;

-- Profile RLS Policies
DROP POLICY IF EXISTS "Users can view own profile" ON public.profile;
CREATE POLICY "Users can view own profile" 
    ON public.profile FOR SELECT 
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update own profile" ON public.profile;
CREATE POLICY "Users can update own profile" 
    ON public.profile FOR UPDATE 
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert own profile" ON public.profile;
CREATE POLICY "Users can insert own profile" 
    ON public.profile FOR INSERT 
    WITH CHECK (auth.uid() = user_id);


-- 2. Create Sessions Table (Corresponds to FocusSessionEntity)
CREATE TABLE IF NOT EXISTS public.sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid(),
    duration INTEGER NOT NULL, -- Total duration of the session in seconds
    goal TEXT NOT NULL, -- Focus task label (e.g. 'Deep Work', 'Coding', 'Reading')
    allowed_apps JSONB NOT NULL DEFAULT '[]'::JSONB, -- Selected apps allowed during focus (List<String>)
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Enable Row Level Security (RLS) for Sessions
ALTER TABLE public.sessions ENABLE ROW LEVEL SECURITY;

-- Sessions RLS Policies
DROP POLICY IF EXISTS "Users can view own sessions" ON public.sessions;
CREATE POLICY "Users can view own sessions" 
    ON public.sessions FOR SELECT 
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert own sessions" ON public.sessions;
CREATE POLICY "Users can insert own sessions" 
    ON public.sessions FOR INSERT 
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update own sessions" ON public.sessions;
CREATE POLICY "Users can update own sessions" 
    ON public.sessions FOR UPDATE 
    USING (auth.uid() = user_id);


-- 3. Automatic Profile Creation Trigger on Sign Up
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profile (user_id, email, display_name, daily_goal, notifications, dark_mode, privacy_mode, preferred_apps, schedule, photo_url)
    VALUES (
        new.id,
        new.email,
        COALESCE(
            new.raw_user_meta_data->>'display_name', 
            new.raw_user_meta_data->>'displayName', 
            split_part(new.email, '@', 1)
        ),
        120,
        TRUE,
        TRUE,
        FALSE,
        '[]'::JSONB,
        '[]'::JSONB,
        COALESCE(
            new.raw_user_meta_data->>'avatar_url',
            new.raw_user_meta_data->>'avatarUrl',
            new.raw_user_meta_data->>'photo_url',
            new.raw_user_meta_data->>'photoUrl'
        )
    )
    ON CONFLICT (user_id) DO NOTHING;
    RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create Trigger
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();


-- 4. RPC statistics calculator: get_focus_stats
-- Calculates user stats, streaks, weekly minutes, and daily focus history
CREATE OR REPLACE FUNCTION public.get_focus_stats(p_user_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_streak INTEGER := 0;
    v_week_minutes INTEGER := 0;
    v_session_count INTEGER := 0;
    v_curr_date DATE := CURRENT_DATE;
    v_has_today BOOLEAN := FALSE;
    v_has_yesterday BOOLEAN := FALSE;
    v_count INTEGER;
    v_daily_stats_json JSONB;
BEGIN
    -- A. Calculate Consecutive Active Days Streak
    -- Check if user had a completed session today
    SELECT COUNT(*) INTO v_count 
    FROM public.sessions 
    WHERE user_id = p_user_id 
      AND completed = TRUE 
      AND (started_at AT TIME ZONE 'UTC')::DATE = CURRENT_DATE;
    
    IF v_count > 0 THEN
        v_has_today := TRUE;
    END IF;
    
    -- Check if user had a completed session yesterday
    SELECT COUNT(*) INTO v_count 
    FROM public.sessions 
    WHERE user_id = p_user_id 
      AND completed = TRUE 
      AND (started_at AT TIME ZONE 'UTC')::DATE = CURRENT_DATE - 1;
      
    IF v_count > 0 THEN
        v_has_yesterday := TRUE;
    END IF;

    IF NOT v_has_today AND NOT v_has_yesterday THEN
        v_streak := 0;
    ELSE
        -- Start counting back from active day anchor
        IF v_has_today THEN
            v_curr_date := CURRENT_DATE;
        ELSE
            v_curr_date := CURRENT_DATE - 1;
        END IF;
        
        LOOP
            SELECT COUNT(*) INTO v_count 
            FROM public.sessions 
            WHERE user_id = p_user_id 
              AND completed = TRUE 
              AND (started_at AT TIME ZONE 'UTC')::DATE = v_curr_date;
              
            EXIT WHEN v_count = 0;
            v_streak := v_streak + 1;
            v_curr_date := v_curr_date - 1;
        END LOOP;
    END IF;

    -- B. Compute total focus minutes in the last 7 calendar days
    SELECT COALESCE(SUM(duration) / 60, 0)::INTEGER INTO v_week_minutes
    FROM public.sessions
    WHERE user_id = p_user_id 
      AND completed = TRUE 
      AND started_at >= NOW() - INTERVAL '7 days';

    -- C. Count total sessions in the last 7 calendar days
    SELECT COUNT(*)::INTEGER INTO v_session_count
    FROM public.sessions
    WHERE user_id = p_user_id 
      AND completed = TRUE
      AND started_at >= NOW() - INTERVAL '7 days';

    -- D. Aggregate chronological 7 Days of stats
    SELECT json_agg(t)::JSONB INTO v_daily_stats_json 
    FROM (
        SELECT 
            to_char(g.d, 'Dy') AS "dayLabel",
            to_char(g.d, 'YYYY-MM-DD') AS "dayDate",
            COALESCE(SUM(s.duration) / 60, 0)::INTEGER AS "minutes"
        FROM (
            SELECT (CURRENT_DATE - i)::DATE AS d
            FROM generate_series(0, 6) AS i
        ) g
        LEFT JOIN public.sessions s ON s.user_id = p_user_id 
          AND s.completed = TRUE 
          AND (s.started_at AT TIME ZONE 'UTC')::DATE = g.d
        GROUP BY g.d
        ORDER BY g.d ASC
    ) t;

    -- E. Package and Return JSON object
    RETURN json_build_object(
        'daily', COALESCE(v_daily_stats_json, '[]'::JSONB),
        'streak', v_streak,
        'weekMinutes', v_week_minutes,
        'sessionCount', v_session_count
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ====================================================================
-- 5. Database Performance Indexes (Optimized for premium scalability)
-- ====================================================================
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON public.sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_completed ON public.sessions (completed);
CREATE INDEX IF NOT EXISTS idx_sessions_started_at ON public.sessions (started_at);

