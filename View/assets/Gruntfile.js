module.exports = function(grunt) {

  var wdkFiles = require('./wdkFiles'),
      path = require('path');

  grunt.initConfig({

    concat: {
      js: {
        src: wdkFiles.libs,
        dest: 'dist/wdk.libs.js'
      }
    },

    uglify: {
      options: {
        mangle: {
          except: ['wdk']
        },
        report: true,
        sourceMap: 'dist/wdk.js.map',
        sourceMappingURL: 'wdk.js.map',
        // sourceMapPrefix: 1
      },
      wdk: {
        files: {
          'dist/wdk.js': wdkFiles.src,
        }
      }
    },

    copy: {
      js: {
        files: [
          {
            expand: true,
            //cwd: 'js',
            src: ['src/**', 'lib/**'],
            dest: 'dist'
          }
        ]
      },
      css: {
        files: [
          {
            expand: true,
            cwd: 'css',
            src: ['**/*'],
            dest: 'dist/css'
          }
        ]
      },
      images: {
        files: [
          {
            expand: true,
            cwd: 'images',
            src: ['**/*'],
            dest: 'dist/images'
          }
        ]
      }
    },

    clean: {
      dist: 'dist'
    }

  });

  grunt.loadNpmTasks('grunt-contrib-clean');
  grunt.loadNpmTasks('grunt-contrib-copy');
  grunt.loadNpmTasks('grunt-contrib-concat');
  grunt.loadNpmTasks('grunt-contrib-uglify');

  grunt.registerTask('dist', ['clean', 'concat', 'uglify', 'copy']);

  grunt.registerTask('default', ['dist']);

};

