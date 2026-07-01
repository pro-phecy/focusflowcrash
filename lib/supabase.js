const { createClient } = require('@supabase/supabase-js');

// Read the URL and public anonymous key from the environment variables
const supabaseUrl = process.env.SUPABASE_URL || '';
const supabaseAnonKey = process.env.SUPABASE_ANON_KEY || '';

if (!supabaseUrl || !supabaseAnonKey) {
  console.warn('Warning: SUPABASE_URL or SUPABASE_ANON_KEY are not configured. Please define them in your environment variables.');
}

// Initialize and export the Supabase Client
const supabase = createClient(supabaseUrl, supabaseAnonKey);

module.exports = { supabase };
