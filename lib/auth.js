const { supabase } = require('./supabase');

/**
 * Register a new user using Supabase Auth, creating a corresponding public Profile entry.
 * @param {string} email
 * @param {string} password
 * @param {string} displayName
 */
async function registerUser(email, password, displayName) {
  try {
    const { data, error } = await supabase.auth.signUp({
      email,
      password,
      options: {
        data: {
          display_name: displayName,
          displayName: displayName
        }
      }
    });

    if (error) throw error;
    return { success: true, user: data.user, session: data.session };
  } catch (err) {
    console.error('Registration error:', err.message);
    return { success: false, error: err.message };
  }
}

/**
 * Log in an existing user with email and password.
 * @param {string} email
 * @param {string} password
 */
async function loginUser(email, password) {
  try {
    const { data, error } = await supabase.auth.signInWithPassword({
      email,
      password
    });

    if (error) throw error;
    return { success: true, user: data.user, session: data.session };
  } catch (err) {
    console.error('Login error:', err.message);
    return { success: false, error: err.message };
  }
}

/**
 * Log out the currently authenticated user session.
 */
async function logoutUser() {
  try {
    const { error } = await supabase.auth.signOut();
    if (error) throw error;
    return { success: true };
  } catch (err) {
    console.error('Logout error:', err.message);
    return { success: false, error: err.message };
  }
}

module.exports = {
  registerUser,
  loginUser,
  logoutUser
};
