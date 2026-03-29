/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        primary: {
          50:  '#f0faf0',
          100: '#d6f0d6',
          200: '#aadcaa',
          300: '#72c272',
          400: '#45a845',
          500: '#2d7a2d',
          600: '#246224',
          700: '#1a4d1a',
          800: '#113811',
          900: '#0a240a',
        },
        brand: {
          red:       '#cc1a1a',
          'red-dark': '#a01414',
          green:     '#2d7a2d',
          'green-dark': '#1a4d1a',
        },
        danger: {
          50:  '#fff1f1',
          100: '#ffe0e0',
          400: '#e05050',
          500: '#cc1a1a',
          600: '#a01414',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
