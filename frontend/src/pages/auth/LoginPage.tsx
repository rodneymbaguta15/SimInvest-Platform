import React, { useState } from "react";
import { useNavigate, useLocation, Link } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from?.pathname || "/dashboard";
  const { login, clearError, error: authError, isLoading } = useAuth();

  const [formData, setFormData] = useState({
    usernameOrEmail: "",
    password: "",
    rememberMe: false,
  });

  const [errors, setErrors] = useState<{ [key: string]: string }>({});

  const validateForm = () => {
    const newErrors: { [key: string]: string } = {};
    if (!formData.usernameOrEmail.trim()) {
      newErrors.usernameOrEmail = "Username or email is required";
    }
    if (!formData.password) {
      newErrors.password = "Password is required";
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));

    // Clear errors when user starts typing
    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: "" }));
    }

    // Clear auth errors when user starts typing
    if (authError) {
      clearError();
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) return;

    try {
      console.log("Submitting login form with:", {
        usernameOrEmail: formData.usernameOrEmail,
        hasPassword: !!formData.password,
      });

      await login({
        usernameOrEmail: formData.usernameOrEmail.trim(),
        password: formData.password,
      });

      console.log("Login successful, navigating to:", from);
      navigate(from, { replace: true });
    } catch (error) {
      console.error("Login failed:", error);

      // The error is already handled by AuthContext, but we can show additional local errors
      let errorMessage = "Login failed. Please check your credentials.";

      if (error instanceof Error) {
        // Use the specific error message if available
        if (
          error.message.includes("Invalid username") ||
          error.message.includes("Invalid password")
        ) {
          errorMessage = "Invalid username/email or password.";
        } else if (
          error.message.includes("network") ||
          error.message.includes("fetch")
        ) {
          errorMessage =
            "Network error. Please check your connection and try again.";
        } else if (error.message) {
          errorMessage = error.message;
        }
      }

      setErrors({ submit: errorMessage });
    }
  };

  // Use auth error if available, otherwise use local error
  const displayError = authError || errors.submit;

  return (
    <div className="min-h-screen flex flex-col relative">
      {/* Background Image */}
      <div
        className="absolute inset-0 bg-cover bg-center bg-no-repeat"
        style={{
          backgroundImage: `url('./log.jpg')`,
        }}
      >
        {/* Dark overlay for better text readability */}
        <div className="absolute inset-0 bg-black/50"></div>
      </div>

      {/* Content with relative positioning to appear above background */}
      <div className="relative z-10 flex-1 flex flex-col">
        {/* Main Content */}
        <div className="flex-1 flex items-center justify-center p-4">
          <div className="w-full max-w-md">
            {/* Header - moved lower, inside the form area */}
            <div className="text-center mb-8">
              <h1 className="text-4xl font-bold text-white mb-2 drop-shadow-lg">
                SimInvest Trading Platform
              </h1>
              <p className="text-gray-200 text-lg drop-shadow">
                Investment Simulation & Learning
              </p>
            </div>

            <form
              onSubmit={handleSubmit}
              className="bg-black/85 backdrop-blur-sm border border-gray-700 p-8 rounded-2xl shadow-2xl space-y-6"
            >
              <h2 className="text-2xl font-bold text-white text-center">
                Sign In
              </h2>

              {displayError && (
                <div className="bg-red-500/20 border border-red-500/50 text-red-200 px-4 py-3 rounded-lg text-sm">
                  {displayError}
                </div>
              )}

              <div>
                <input
                  type="text"
                  name="usernameOrEmail"
                  value={formData.usernameOrEmail}
                  onChange={handleChange}
                  placeholder="Username or Email"
                  className="w-full px-4 py-3 bg-gray-800/90 border border-gray-600 rounded-lg text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  disabled={isLoading}
                />
                {errors.usernameOrEmail && (
                  <p className="text-red-400 text-sm mt-1">
                    {errors.usernameOrEmail}
                  </p>
                )}
              </div>

              <div>
                <input
                  type="password"
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  placeholder="Password"
                  className="w-full px-4 py-3 bg-gray-800/90 border border-gray-600 rounded-lg text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  disabled={isLoading}
                />
                {errors.password && (
                  <p className="text-red-400 text-sm mt-1">{errors.password}</p>
                )}
              </div>

              <div className="flex items-center justify-between text-sm text-gray-200">
                <label className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    name="rememberMe"
                    checked={formData.rememberMe}
                    onChange={handleChange}
                    disabled={isLoading}
                    className="rounded border-gray-600 bg-gray-800 text-blue-600 focus:ring-blue-500 focus:ring-offset-0"
                  />
                  <span>Remember me</span>
                </label>
                <Link
                  to="/register"
                  className="text-blue-400 hover:underline hover:text-blue-300 transition-colors"
                >
                  Create Account
                </Link>
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className="w-full bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold py-3 px-4 rounded-lg transition-colors duration-200"
              >
                {isLoading ? (
                  <span className="flex items-center justify-center">
                    <svg
                      className="animate-spin -ml-1 mr-3 h-5 w-5 text-white"
                      xmlns="http://www.w3.org/2000/svg"
                      fill="none"
                      viewBox="0 0 24 24"
                    >
                      <circle
                        className="opacity-25"
                        cx="12"
                        cy="12"
                        r="10"
                        stroke="currentColor"
                        strokeWidth="4"
                      ></circle>
                      <path
                        className="opacity-75"
                        fill="currentColor"
                        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                      ></path>
                    </svg>
                    Signing in...
                  </span>
                ) : (
                  "Sign In"
                )}
              </button>
            </form>
          </div>
        </div>
      </div>

      {/* Footer */}
      <footer className="relative z-10 bg-black/80 backdrop-blur-sm border-t border-gray-700 py-6 px-4">
        <div className="max-w-6xl mx-auto">
          <div className="flex flex-col md:flex-row justify-between items-center space-y-4 md:space-y-0">
            <div className="text-center md:text-left">
              <p className="text-gray-300 text-sm">
                © 2025 Rodney Mbaguta. All rights reserved.
              </p>
            </div>
            <div className="flex flex-wrap justify-center md:justify-end gap-6 text-sm">
              <Link
                to="/terms"
                className="text-gray-300 hover:text-white transition-colors"
              >
                Terms of Service
              </Link>
              <Link
                to="/privacy"
                className="text-gray-300 hover:text-white transition-colors"
              >
                Privacy Policy
              </Link>
              <Link
                to="/contact"
                className="text-gray-300 hover:text-white transition-colors"
              >
                Contact Us
              </Link>
              <Link
                to="/help"
                className="text-gray-300 hover:text-white transition-colors"
              >
                Help Center
              </Link>
            </div>
          </div>
          <div className="mt-4 text-center">
            <p className="text-xs text-gray-400">
              SimInvest Platform - Educational investment simulation for
              learning purposes only. Not real investment advice.
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
